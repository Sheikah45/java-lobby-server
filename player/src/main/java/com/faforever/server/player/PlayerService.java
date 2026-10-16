package com.faforever.server.player;

import com.faforever.server.domain.FriendOrFoeEntity;
import com.faforever.server.message.client.AdminMessage;
import com.faforever.server.message.client.ConnectionMessage;
import com.faforever.server.message.client.LobbyMessage;
import com.faforever.server.message.client.SocialMessage;
import com.faforever.server.message.client.dto.AvatarInfo;
import com.faforever.server.message.client.dto.PlayerInfo;
import com.faforever.server.message.service.InboundLobbyMessage;
import com.faforever.server.message.service.OutboundLobbyMessage;
import com.faforever.server.message.service.PlayerConnectionDetails;
import com.faforever.server.messagebroker.MessageBrokerAPIClient;
import com.faforever.server.rating.LeaderboardRating;
import com.faforever.server.rating.LeaderboardRatingRepository;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.jbosslog.JBossLog;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@JBossLog
@ApplicationScoped
public class PlayerService {

    private final Emitter<OutboundLobbyMessage<LobbyMessage.Server>> outboundLobbyMessageEmitter;

    private final LeaderboardRatingRepository leaderboardRatingRepository;
    private final PlayerRepository playerRepository;
    private final FriendOrFoeRepository friendOrFoeRepository;
    private final AssignedAvatarRepository assignedAvatarRepository;
    private final UserGroupAssignmentRepository userGroupAssignmentRepository;

    private final MessageBrokerAPIClient messageBrokerAPIClient;

    private final Map<Integer, Player> playerIdMap = new ConcurrentHashMap<>();

    private final Map<Long, Player> sessionPlayerMap = new ConcurrentHashMap<>();

    @Getter(AccessLevel.PACKAGE)
    private final Set<Player> dirtyPlayers = ConcurrentHashMap.newKeySet();
    @Getter(AccessLevel.PACKAGE)
    private final Set<Player> disconnectedPlayers = ConcurrentHashMap.newKeySet();

    private OffsetDateTime lastRatingUpdateTime = OffsetDateTime.now();

    PlayerService(
            @Channel("lobby-outbound") Emitter<OutboundLobbyMessage<LobbyMessage.Server>> outboundLobbyMessageEmitter,
            LeaderboardRatingRepository leaderboardRatingRepository, PlayerRepository playerRepository,
            FriendOrFoeRepository friendOrFoeRepository, AssignedAvatarRepository assignedAvatarRepository,
            UserGroupAssignmentRepository userGroupAssignmentRepository,
            @RestClient MessageBrokerAPIClient messageBrokerAPIClient) {
        this.outboundLobbyMessageEmitter = outboundLobbyMessageEmitter;
        this.leaderboardRatingRepository = leaderboardRatingRepository;
        this.playerRepository = playerRepository;
        this.friendOrFoeRepository = friendOrFoeRepository;
        this.assignedAvatarRepository = assignedAvatarRepository;
        this.userGroupAssignmentRepository = userGroupAssignmentRepository;
        this.messageBrokerAPIClient = messageBrokerAPIClient;
    }

    @PostConstruct
    void registerExistingPlayers() {
        messageBrokerAPIClient.getActiveSessions()
                              .stream()
                              .map(playerConnectionDetails -> markPlayerOnline(playerConnectionDetails.sessionId(),
                                      playerConnectionDetails.playerId()))
                              .forEach(this::markDirty);
    }

    @Incoming("player-connection")
    @RunOnVirtualThread
    void handlePlayerConnectionDetails(PlayerConnectionDetails playerConnectionDetails) {
        if (playerConnectionDetails.connected()) {
            registerSessionForPlayer(playerConnectionDetails.sessionId(), playerConnectionDetails.playerId());
        } else {
            unregisterSession(playerConnectionDetails.sessionId());
        }
    }

    void registerSessionForPlayer(long sessionId, int playerId) {
        Player player = markPlayerOnline(sessionId, playerId);
        sendWelcomeMessages(sessionId);
        markDirty(player);
    }

    private void sendWelcomeMessages(long sessionId) {
        Player player = sessionPlayerMap.get(sessionId);
        if (player == null) {
            throw new IllegalStateException("No player associated with session");
        }

        Set<String> channels = new HashSet<>();
        player.getClan().map("#%s_clan"::formatted).ifPresent(channels::add);
        if (userGroupAssignmentRepository.isPlayerModerator(player.getId())) {
            channels.add("#moderators");
        }

        outboundLobbyMessageEmitter.send(OutboundLobbyMessage.forSession(sessionId,
                new ConnectionMessage.LoginSuccessResponse(PlayerInfo.from(player), OffsetDateTime.now())));
        outboundLobbyMessageEmitter.send(OutboundLobbyMessage.forSession(sessionId,
                new SocialMessage.SocialInfo(channels, player.getFriendIds(), player.getFoeIds())));
        outboundLobbyMessageEmitter.send(OutboundLobbyMessage.forSession(sessionId, new SocialMessage.PlayerInfoList(
                playerIdMap.values().stream().map(PlayerInfo::from).collect(Collectors.toSet()))));
    }

    private Player markPlayerOnline(long sessionId, int playerId) {
        Player player = playerIdMap.computeIfAbsent(playerId, playerRepository::loadPlayer);
        if (player == null) {
            outboundLobbyMessageEmitter.send(OutboundLobbyMessage.forSession(sessionId,
                    new ConnectionMessage.LoginFailureResponse("No user exists with id")));
            throw new IllegalStateException("No user with id %d".formatted(playerId));
        }
        Player existingPlayer = sessionPlayerMap.putIfAbsent(sessionId, player);
        if (existingPlayer != null && existingPlayer != player) {
            throw new IllegalStateException(
                    "Session already has an existing player of id %d".formatted(existingPlayer.getId()));
        }
        player.addSession(sessionId);
        return player;
    }

    void unregisterSession(long sessionId) {
        Player player = sessionPlayerMap.remove(sessionId);
        if (player == null) {
            return;
        }

        player.removeSession(sessionId);
        if (player.hasNoSession()) {
            markDisconnected(player);
        }
    }

    @Incoming("admin-inbound")
    public void handleAdminRequest(InboundLobbyMessage<AdminMessage.Client> request) {
        Player player = playerIdMap.get(request.playerId());
        if (player == null) {
            return;
        }
        switch (request.message()) {
            case AdminMessage.BroadcastRequest broadcastRequest -> broadcast(player, broadcastRequest);
            case AdminMessage.KickPlayerRequest kickPlayerRequest -> kickPlayer(player, kickPlayerRequest);
            case AdminMessage.ClosePlayerGameRequest closePlayerGameRequest -> closePlayerGame(player, closePlayerGameRequest);
        }
    }

    private void broadcast(Player player, AdminMessage.BroadcastRequest broadcastRequest) {
        String message = broadcastRequest.message();
        if (message.isBlank()) {
            return;
        }

        if (userGroupAssignmentRepository.playerLacksPermission(player.getId(), "ADMIN_BROADCAST_MESSAGE")) {
            LOG.warnf("Unauthorized broadcast request: %s", broadcastRequest.message());
            return;
        }

        outboundLobbyMessageEmitter.send(OutboundLobbyMessage.forAll(new AdminMessage.NoticeInfo(message, AdminMessage.Style.INFO)));
    }

    private void kickPlayer(Player player, AdminMessage.KickPlayerRequest kickPlayerRequest) {
        if (userGroupAssignmentRepository.playerLacksPermission(player.getId(), "ADMIN_KICK_SERVER")) {
            LOG.warnf("Unauthorized kick request with target: %d", kickPlayerRequest.playerId());
            return;
        }

        outboundLobbyMessageEmitter.send(OutboundLobbyMessage.forPlayer(kickPlayerRequest.playerId(), new AdminMessage.NoticeInfo(null, AdminMessage.Style.KICK)));
    }

    private void closePlayerGame(Player player, AdminMessage.ClosePlayerGameRequest closePlayerGameRequest) {
        if (userGroupAssignmentRepository.playerLacksPermission(player.getId(), "ADMIN_KICK_SERVER")) {
            LOG.warnf("Unauthorized kick request with target: %d", closePlayerGameRequest.playerId());
            return;
        }

        outboundLobbyMessageEmitter.send(OutboundLobbyMessage.forPlayer(closePlayerGameRequest.playerId(), new AdminMessage.NoticeInfo(null, AdminMessage.Style.KILL)));
    }

    @Incoming("social-inbound")
    public void handleSocialRequest(InboundLobbyMessage<SocialMessage.Client> request) {
        Player player = playerIdMap.get(request.playerId());
        if (player == null) {
            return;
        }
        switch (request.message()) {
            case SocialMessage.ListAvatarsRequest _ -> sendAvatars(player, request.sessionId());
            case SocialMessage.SelectAvatarRequest selectAvatarRequest -> selectAvatar(player, selectAvatarRequest);
            case SocialMessage.RemoveAvatarRequest _ -> removeAvatar(player);
            case SocialMessage.SocialAddRequest(Integer friendId, Integer foeId) -> {
                if (friendId != null) {
                    addSocialRelationship(player, friendId, FriendOrFoeEntity.Status.FRIEND);
                }
                if (foeId != null) {
                    addSocialRelationship(player, foeId, FriendOrFoeEntity.Status.FOE);
                }
            }
            case SocialMessage.SocialRemoveRequest(Integer friendId, Integer foeId) -> {
                if (friendId != null) {
                    removeSocialRelationship(player, friendId);
                }
                if (foeId != null) {
                    removeSocialRelationship(player, foeId);
                }
            }
        }
    }

    private void addSocialRelationship(Player player, int targetId, FriendOrFoeEntity.Status status) {
        friendOrFoeRepository.upsertPlayerRelationship(player.getId(), targetId, status);
        switch (status) {
            case FOE -> player.addFoe(targetId);
            case FRIEND -> player.addFriend(targetId);
        }
    }

    private void removeSocialRelationship(Player player, int targetId) {
        friendOrFoeRepository.deletePlayerRelationship(player.getId(), targetId);
        player.removeFriendOrFoe(targetId);
    }

    private void sendAvatars(Player player, long sessionId) {
        Set<AvatarInfo> avatars = assignedAvatarRepository.findAssignedAvatarsByPlayer(player.getId())
                                                          .stream()
                                                          .map(AvatarInfo::from)
                                                          .collect(Collectors.toSet());
        SocialMessage.AvatarInfoList avatarInfoList = new SocialMessage.AvatarInfoList(avatars);
        outboundLobbyMessageEmitter.send(OutboundLobbyMessage.forSession(sessionId, avatarInfoList));
    }

    private void selectAvatar(Player player, SocialMessage.SelectAvatarRequest selectRequest) {
        String newAvatarUrl = selectRequest.avatarUrl();
        if (player.getAvatar().map(avatar -> newAvatarUrl.equals(avatar.url())).orElse(false)) {
            return;
        }

        Avatar avatar = assignedAvatarRepository.updateSelectedAvatar(player.getId(), newAvatarUrl);
        player.setAvatar(avatar);
        markDirty(player);
    }

    private void removeAvatar(Player player) {
        if (player.getAvatar().isEmpty()) {
            return;
        }

        assignedAvatarRepository.removeSelectedAvatar(player.getId());
        player.clearAvatar();
        markDirty(player);
    }

    void markDisconnected(Player player) {
        if (!playerIdMap.containsKey(player.getId())) {
            return;
        }
        disconnectedPlayers.add(player);
    }

    void markDirty(Player player) {
        if (!playerIdMap.containsKey(player.getId())) {
            return;
        }
        disconnectedPlayers.remove(player);
        dirtyPlayers.add(player);
    }

    @RunOnVirtualThread
    @Scheduled(every = "1m", skipExecutionIf = Scheduled.ApplicationNotRunning.class, concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void updatePlayerLeaderboardRatings() {
        OffsetDateTime currentRatingUpdateTime = lastRatingUpdateTime;
        lastRatingUpdateTime = OffsetDateTime.now();
        Map<Integer, Set<LeaderboardRating>> playerIdRatingMap = leaderboardRatingRepository.loadUpdatedLeaderboardRatings(
                Set.copyOf(playerIdMap.keySet()), currentRatingUpdateTime);

        playerIdRatingMap.forEach((playerId, newLeaderboardRatings) -> {
            Player player = playerIdMap.get(playerId);
            if (player == null) {
                return;
            }

            player.updateLeaderboardRatings(newLeaderboardRatings);
            markDirty(player);
        });
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

    @RunOnVirtualThread
    @Scheduled(every = "1s", skipExecutionIf = Scheduled.ApplicationNotRunning.class, concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void removeDisconnectedPlayers() {
        Set<Player> frozenDisconnectedPlayers = Set.copyOf(disconnectedPlayers);
        if (frozenDisconnectedPlayers.isEmpty()) {
            return;
        }

        frozenDisconnectedPlayers.stream().map(Player::getId).forEach(playerIdMap::remove);

        dirtyPlayers.addAll(frozenDisconnectedPlayers);
        disconnectedPlayers.removeAll(frozenDisconnectedPlayers);
    }
}
