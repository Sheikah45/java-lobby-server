package com.faforever.server.player;

import com.faforever.server.domain.FriendOrFoeEntity;
import com.faforever.server.message.client.ConnectionMessage;
import com.faforever.server.message.client.LobbyMessage;
import com.faforever.server.message.client.SocialMessage;
import com.faforever.server.message.client.dto.PlayerInfo;
import com.faforever.server.message.service.OutboundLobbyMessage;
import com.faforever.server.message.service.PlayerSessionState;
import com.faforever.server.rating.LeaderboardRating;
import com.faforever.server.rating.LeaderboardRatingRepository;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.AccessLevel;
import lombok.Getter;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@ApplicationScoped
class PlayerStateTracker {

    private final Emitter<Map<String, String>> playerConnectionsRequestEmitter;
    private final Emitter<OutboundLobbyMessage<LobbyMessage.Server>> outboundLobbyMessageEmitter;

    private final PlayerRepository playerRepository;
    private final FriendOrFoeRepository friendOrFoeRepository;
    private final UserGroupAssignmentRepository userGroupAssignmentRepository;
    private final LeaderboardRatingRepository leaderboardRatingRepository;

    private final Map<Integer, Player> playerIdMap = new ConcurrentHashMap<>();
    private final Map<Long, Player> sessionPlayerMap = new ConcurrentHashMap<>();
    private final Map<Player, Set<Long>> playerSessionMap = new ConcurrentHashMap<>();

    @Getter(AccessLevel.PACKAGE)
    private final Set<Player> dirtyPlayers = ConcurrentHashMap.newKeySet();

    private Instant lastRatingUpdateTime = Instant.now();

    public PlayerStateTracker(
            @Channel("player-connection-request") Emitter<Map<String, String>> playerConnectionsRequestEmitter, @Channel("lobby-outbound") Emitter<OutboundLobbyMessage<LobbyMessage.Server>> outboundLobbyMessageEmitter,
            PlayerRepository playerRepository, FriendOrFoeRepository friendOrFoeRepository,
            UserGroupAssignmentRepository userGroupAssignmentRepository,
            LeaderboardRatingRepository leaderboardRatingRepository) {
        this.playerConnectionsRequestEmitter = playerConnectionsRequestEmitter;
        this.outboundLobbyMessageEmitter = outboundLobbyMessageEmitter;
        this.playerRepository = playerRepository;
        this.friendOrFoeRepository = friendOrFoeRepository;
        this.userGroupAssignmentRepository = userGroupAssignmentRepository;
        this.leaderboardRatingRepository = leaderboardRatingRepository;
    }

    @PostConstruct
    void registerExistingPlayers() {
        playerConnectionsRequestEmitter.send(Map.of());
    }

    @Incoming("player-connection-response")
    @RunOnVirtualThread
    void handlePlayerSessionStatus(PlayerSessionState playerSessionStatus) {
        switch (playerSessionStatus.state()) {
            case PlayerSessionState.State.New(Duration secondsToExpiry) -> registerPlayerSession(playerSessionStatus.sessionId(), playerSessionStatus.playerId(),
                                                                                                 true);

            case PlayerSessionState.State.Existing(Duration secondsToExpiry) -> registerPlayerSession(playerSessionStatus.sessionId(), playerSessionStatus.playerId(),
                                                   false);
            case PlayerSessionState.State.Gone() -> unregisterPlayerSession(playerSessionStatus.sessionId(),
                                                          playerSessionStatus.playerId());
        }
    }

    private void registerPlayerSession(long sessionId, int playerId, boolean newConnection) {
        Player player = playerIdMap.computeIfAbsent(playerId, playerRepository::loadPlayer);
        if (player == null) {
            throw new IllegalStateException("No user with id %d".formatted(playerId));
        }
        Player existingPlayer = sessionPlayerMap.putIfAbsent(sessionId, player);
        if (existingPlayer != null && existingPlayer != player) {
            throw new IllegalStateException(
                    "Session already has an existing player of id %d".formatted(existingPlayer.getId()));
        }
        playerSessionMap.compute(player, (_, sessions) -> {
            if (sessions == null) {
                sessions = ConcurrentHashMap.newKeySet();
            }
            sessions.add(sessionId);
            return sessions;
        });

        if (newConnection) {
            sendWelcomeMessages(sessionId, player);
            dirtyPlayers.add(player);
        }
    }

    private void sendWelcomeMessages(long sessionId, Player player) {
        Set<String> channels = new HashSet<>();
        player.getClan().map("#%s_clan"::formatted).ifPresent(channels::add);
        if (userGroupAssignmentRepository.isPlayerModerator(player.getId())) {
            channels.add("#moderators");
        }

        Map<FriendOrFoeEntity.Status, Set<Integer>> friendsAndFoes = friendOrFoeRepository.loadFriendsAndFoes(
                player.getId());

        outboundLobbyMessageEmitter.send(OutboundLobbyMessage.forSession(sessionId,
                                                                         new ConnectionMessage.LoginSuccessResponse(
                                                                                 PlayerInfo.from(player),
                                                                                 OffsetDateTime.now())));
        outboundLobbyMessageEmitter.send(OutboundLobbyMessage.forSession(sessionId,
                                                                         new SocialMessage.SocialInfo(channels,
                                                                                                      friendsAndFoes.getOrDefault(
                                                                                                              FriendOrFoeEntity.Status.FRIEND,
                                                                                                              Set.of()),
                                                                                                      friendsAndFoes.getOrDefault(
                                                                                                              FriendOrFoeEntity.Status.FOE,
                                                                                                              Set.of()))));
        outboundLobbyMessageEmitter.send(OutboundLobbyMessage.forSession(sessionId, new SocialMessage.PlayerInfoList(
                playerIdMap.values().stream().map(PlayerInfo::from).collect(Collectors.toSet()))));
    }

    private void unregisterPlayerSession(long sessionId, int playerId) {
        playerIdMap.computeIfPresent(playerId, (_, player) -> {
            if (!sessionPlayerMap.remove(sessionId, player)) {
                return player;
            }

            Set<Long> remainingSessions = playerSessionMap.computeIfPresent(player, (_, sessions) -> {
                if (sessions.remove(sessionId) && sessions.isEmpty()) {
                    return null;
                } else {
                    return sessions;
                }
            });

            if (remainingSessions == null) {
                player.markOffline();
                dirtyPlayers.add(player);
                return null;
            } else {
                return player;
            }
        });
    }

    @RunOnVirtualThread
    @Scheduled(every = "1m", skipExecutionIf = Scheduled.ApplicationNotRunning.class, concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void updatePlayerLeaderboardRatings() {
        Instant currentRatingUpdateTime = lastRatingUpdateTime;
        lastRatingUpdateTime = Instant.now();
        Set<Integer> playerIds = Set.copyOf(playerIdMap.keySet());
        if (playerIds.isEmpty()) {
            return;
        }

        Map<Integer, Set<LeaderboardRating>> playerIdRatingMap = leaderboardRatingRepository.loadUpdatedLeaderboardRatings(
                playerIds, currentRatingUpdateTime);

        playerIdRatingMap.forEach((playerId, newLeaderboardRatings) -> updatePlayer(playerId,
                                                                                    player -> player.updateLeaderboardRatings(
                                                                                            newLeaderboardRatings)));
    }

    @RunOnVirtualThread
    @Scheduled(every = "1s", skipExecutionIf = Scheduled.ApplicationNotRunning.class, concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void sendUpdateForDirtyPlayers() {
        Set<Player> frozenDirtyPlayers = Set.copyOf(dirtyPlayers);
        if (frozenDirtyPlayers.isEmpty()) {
            return;
        }

        Set<PlayerInfo> playerInfos = frozenDirtyPlayers.stream().map(PlayerInfo::from).collect(Collectors.toSet());
        outboundLobbyMessageEmitter.send(OutboundLobbyMessage.forAll(new SocialMessage.PlayerInfoList(playerInfos)));

        dirtyPlayers.removeAll(frozenDirtyPlayers);
    }

    void updatePlayer(int playerId, Consumer<Player> updater) {
        Player player = playerIdMap.get(playerId);
        if (player == null) {
            return;
        }
        updater.accept(player);
        dirtyPlayers.add(player);
    }
}
