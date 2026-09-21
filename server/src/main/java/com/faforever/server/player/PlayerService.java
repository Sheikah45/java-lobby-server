package com.faforever.server.player;

import com.faforever.server.domain.FriendOrFoeEntity;
import com.faforever.server.game.GameService;
import com.faforever.server.message.MessageBroker;
import com.faforever.server.message.external.ConnectionMessage;
import com.faforever.server.message.external.SocialMessage;
import com.faforever.server.message.external.dto.DtoMapper;
import com.faforever.server.message.external.dto.PlayerInfo;
import com.faforever.server.message.internal.InboundLobbyMessage;
import com.faforever.server.message.internal.OutboundLobbyMessage;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@JBossLog
@ApplicationScoped
@RequiredArgsConstructor
public class PlayerService {

    private final GameService gameService;

    private final MessageBroker messageBroker;

    private final PlayerRepository playerRepository;
    private final FriendOrFoeRepository friendOrFoeRepository;
    private final AssignedAvatarRepository assignedAvatarRepository;
    private final UserGroupAssignmentRepository userGroupAssignmentRepository;

    private final DtoMapper dtoMapper;

    private final Map<Integer, Player> playerIdMap = new ConcurrentHashMap<>();

    private final Map<Long, Player> sessionPlayerMap = new ConcurrentHashMap<>();

    @Getter(AccessLevel.PACKAGE)
    private final Set<Player> dirtyPlayers = ConcurrentHashMap.newKeySet();
    @Getter(AccessLevel.PACKAGE)
    private final Set<Player> disconnectedPlayers = ConcurrentHashMap.newKeySet();

    public void registerSessionForPlayer(long sessionId, int playerId) {
        Player player = playerIdMap.computeIfAbsent(playerId, playerRepository::loadPlayer);
        Player existingPlayer = sessionPlayerMap.putIfAbsent(sessionId, player);
        if (existingPlayer != null && existingPlayer != player) {
            throw new IllegalStateException(
                    "Session already has an existing player of id %d".formatted(existingPlayer.getId()));
        }

        Set<String> channels = new HashSet<>();
        player.getClan().map("#%s_clan"::formatted).ifPresent(channels::add);
        if (userGroupAssignmentRepository.isPlayerModerator(playerId)) {
            channels.add("#moderators");
        }

        messageBroker.handleOutboundMessage(OutboundLobbyMessage.forSession(sessionId,
                new ConnectionMessage.LoginSuccessResponse(dtoMapper.map(player), OffsetDateTime.now())));
        messageBroker.handleOutboundMessage(OutboundLobbyMessage.forSession(sessionId,
                new SocialMessage.SocialInfo(channels, player.getFriendIds(), player.getFoeIds())));
        messageBroker.handleOutboundMessage(OutboundLobbyMessage.forSession(sessionId,
                new SocialMessage.PlayerInfoList(dtoMapper.map(playerIdMap.values()))));

        gameService.sendGamesToSession(sessionId);

        player.addSession(sessionId);
        markDirty(player);
    }

    public void unregisterSession(long sessionId) {
        Player player = sessionPlayerMap.remove(sessionId);
        if (player == null) {
            return;
        }

        player.removeSession(sessionId);
        if (player.hasNoSession()) {
            markDisconnected(player);
        }
    }

    public void handleRequest(InboundLobbyMessage<SocialMessage.Client> request) {
        int playerId = request.playerId();
        switch (request.message()) {
            case SocialMessage.ListAvatarsRequest _ -> sendAvatars(request.sessionId());
            case SocialMessage.SelectAvatarRequest selectAvatarRequest -> selectAvatar(playerId, selectAvatarRequest);
            case SocialMessage.RemoveAvatarRequest _ -> removeAvatar(playerId);
            case SocialMessage.SocialAddRequest(Integer friendId, Integer foeId) -> {
                if (friendId != null) {
                    addSocialRelationship(playerId, friendId, FriendOrFoeEntity.Status.FRIEND);
                }
                if (foeId != null) {
                    addSocialRelationship(playerId, foeId, FriendOrFoeEntity.Status.FOE);
                }
            }
            case SocialMessage.SocialRemoveRequest(Integer friendId, Integer foeId) -> {
                if (friendId != null) {
                    removeSocialRelationship(playerId, friendId);
                }
                if (foeId != null) {
                    removeSocialRelationship(playerId, foeId);
                }
            }
        }
    }

    private void addSocialRelationship(int playerId, int targetId, FriendOrFoeEntity.Status status) {
        Player player = playerIdMap.get(playerId);
        if (player == null) {
            return;
        }
        friendOrFoeRepository.upsertPlayerRelationship(player.getId(), targetId, status);
        switch (status) {
            case FOE -> player.addFoe(targetId);
            case FRIEND -> player.addFriend(targetId);
        }
        //TODO: Handle game visibility changes
    }

    private void removeSocialRelationship(int playerId, int targetId) {
        Player player = playerIdMap.get(playerId);
        if (player == null) {
            return;
        }
        friendOrFoeRepository.deletePlayerRelationship(player.getId(), targetId);
        player.removeFriendOrFoe(targetId);
        //TODO: Handle game visibility changes
    }

    private void sendAvatars(long sessionId) {
        Player player = sessionPlayerMap.get(sessionId);
        if (player == null) {
            return;
        }
        Set<Avatar> avatars = assignedAvatarRepository.findAssignedAvatarsByPlayer(player.getId());
        SocialMessage.AvatarInfoList avatarInfoList = new SocialMessage.AvatarInfoList(dtoMapper.mapAvatars(avatars));
        messageBroker.handleOutboundMessage(OutboundLobbyMessage.forSession(sessionId, avatarInfoList));
    }

    private void selectAvatar(int playerId, SocialMessage.SelectAvatarRequest selectRequest) {
        Player player = playerIdMap.get(playerId);
        if (player == null) {
            return;
        }

        String newAvatarUrl = selectRequest.avatarUrl();
        if (player.getAvatar().map(avatar -> newAvatarUrl.equals(avatar.url())).orElse(false)) {
            return;
        }

        Avatar avatar = assignedAvatarRepository.updateSelectedAvatar(player.getId(), newAvatarUrl);
        player.setAvatar(avatar);
        markDirty(player);
    }

    private void removeAvatar(int playerId) {
        Player player = playerIdMap.get(playerId);
        if (player == null) {
            return;
        }

        if (player.getAvatar().isEmpty()) {
            return;
        }

        assignedAvatarRepository.removeSelectedAvatar(player.getId());
        player.clearAvatar();
        markDirty(player);
    }

    public Player getSessionPlayer(long sessionId) {
        Player player = sessionPlayerMap.get(sessionId);
        if (player == null) {
            throw new IllegalArgumentException("Session id %d not associated with a player".formatted(sessionId));
        }
        return player;
    }

    public Player getActivePlayer(int playerId) {
        Player player = playerIdMap.get(playerId);
        if (player == null) {
            throw new IllegalArgumentException("Player is not online");
        }
        return player;
    }

    public Set<Player> getOnlinePlayers() {
        return Set.copyOf(playerIdMap.values());
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
    @Scheduled(every = "1s", skipExecutionIf = Scheduled.ApplicationNotRunning.class,
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void sendUpdateForDirtyPlayers() {
        Set<Player> frozenDirtyPlayers = Set.copyOf(dirtyPlayers);
        if (frozenDirtyPlayers.isEmpty()) {
            return;
        }

        Set<PlayerInfo> playerInfos = dtoMapper.map(frozenDirtyPlayers);
        messageBroker.handleOutboundMessage(OutboundLobbyMessage.forAll(new SocialMessage.PlayerInfoList(playerInfos)));

        dirtyPlayers.removeAll(frozenDirtyPlayers);
    }

    @RunOnVirtualThread
    @Scheduled(every = "1s", skipExecutionIf = Scheduled.ApplicationNotRunning.class,
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
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
