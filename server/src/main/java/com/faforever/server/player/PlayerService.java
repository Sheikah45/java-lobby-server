package com.faforever.server.player;

import com.faforever.server.broadcast.BroadcastService;
import com.faforever.server.connection.SessionController;
import com.faforever.server.domain.FriendOrFoeEntity;
import com.faforever.server.message.AdminMessage;
import com.faforever.server.message.ConnectionMessage;
import com.faforever.server.message.SocialMessage;
import com.faforever.server.message.dto.DtoMapper;
import com.faforever.server.message.dto.PlayerInfo;
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
@RequiredArgsConstructor
@ApplicationScoped
public class PlayerService {

    private final BroadcastService broadcastService;

    private final PlayerRepository playerRepository;
    private final FriendOrFoeRepository friendOrFoeRepository;
    private final AssignedAvatarRepository assignedAvatarRepository;

    private final DtoMapper dtoMapper;

    private final Map<Integer, Player> playerIdMap = new ConcurrentHashMap<>();

    private final Map<Player, Set<SessionController>> playerSessionControllerMap = new ConcurrentHashMap<>();

    @Getter(AccessLevel.PACKAGE)
    private final Set<Player> dirtyPlayers = ConcurrentHashMap.newKeySet();
    @Getter(AccessLevel.PACKAGE)
    private final Set<Player> disconnectedPlayers = ConcurrentHashMap.newKeySet();

    public void registerSession(SessionController sessionController) {
        int playerId = sessionController.playerId().orElseThrow();
        Player player = playerIdMap.computeIfAbsent(playerId, playerRepository::loadPlayer);

        sessionController.sendMessage(
                new ConnectionMessage.LoginSuccessResponse(dtoMapper.map(player), OffsetDateTime.now()));

        Set<String> channels = new HashSet<>();
        player.getClan().map("#%s_clan"::formatted).ifPresent(channels::add);
        sessionController.sendMessage(
                new SocialMessage.SocialInfo(channels, player.getFriendIds(), player.getFoeIds()));

        sessionController.sendMessage(new SocialMessage.PlayerInfoList(dtoMapper.map(playerIdMap.values())));

        Set<SessionController> playerSessionControllers = playerSessionControllerMap.computeIfAbsent(player,
                _ -> ConcurrentHashMap.newKeySet());
        boolean newPlayer = playerSessionControllers.isEmpty();
        playerSessionControllers.add(sessionController);
        if (newPlayer) {
            markDirty(player);
        }
    }

    public void unregisterSession(SessionController sessionController) {
        sessionController.playerId().map(playerIdMap::get).ifPresent(player -> {
            Set<SessionController> playerSessions = playerSessionControllerMap.getOrDefault(player, Set.of());
            playerSessions.remove(sessionController);
            if (playerSessions.isEmpty()) {
                markDisconnected(player);
            }
        });
    }

    public void kickPlayer(int playerId) {
        Player player = playerIdMap.get(playerId);
        if (player == null) {
            return;
        }

        playerSessionControllerMap.getOrDefault(player, Set.of()).forEach(SessionController::kick);
    }

    public void closePlayerGame(int playerId) {
        Player player = playerIdMap.get(playerId);
        if (player == null) {
            return;
        }

        playerSessionControllerMap.getOrDefault(player, Set.of())
                                  .forEach(sessionController -> sessionController.sendMessage(
                                          new AdminMessage.NoticeInfo(null, AdminMessage.Style.KILL)));
    }

    public void changeSocialRelationship(SocialRequest.FriendOrFoe friendOrFoeRequest) {
        int playerId = friendOrFoeRequest.playerId();
        int targetId = friendOrFoeRequest.targetId();
        Player player = playerIdMap.get(playerId);
        if (player == null) {
            return;
        }
        switch (friendOrFoeRequest) {
            case SocialRequest.FriendOrFoe.Add(_, _, SocialRequest.FriendOrFoe.Status status) -> {
                FriendOrFoeEntity.Status entityStatus = switch (status) {
                    case FOE -> FriendOrFoeEntity.Status.FOE;
                    case FRIEND -> FriendOrFoeEntity.Status.FRIEND;
                };
                friendOrFoeRepository.upsertPlayerRelationship(playerId, targetId, entityStatus);
                switch (entityStatus) {
                    case FOE -> player.addFoe(targetId);
                    case FRIEND -> player.addFriend(targetId);
                }
            }
            case SocialRequest.FriendOrFoe.Remove _ -> {
                friendOrFoeRepository.deletePlayerRelationship(playerId, targetId);
                player.removeFriendOrFoe(targetId);
            }
        }
    }

    public Set<Avatar> getAvatars(SocialRequest.Avatars avatarsRequest) {
        return assignedAvatarRepository.findAssignedAvatarsByPlayer(avatarsRequest.playerId());
    }

    public void selectAvatar(SocialRequest.SelectAvatar selectRequest) {
        int playerId = selectRequest.playerId();
        Player player = playerIdMap.get(playerId);
        if (player == null) {
            return;
        }

        String newAvatarUrl = selectRequest.avatarUrl();
        if (player.getAvatar().map(avatar -> newAvatarUrl.equals(avatar.url())).orElse(false)) {
            return;
        }

        Avatar avatar = assignedAvatarRepository.updateSelectedAvatar(playerId, newAvatarUrl);
        player.setAvatar(avatar);
        markDirty(player);
    }

    public void removeAvatar(SocialRequest.RemoveAvatar removeRequest) {
        int playerId = removeRequest.playerId();
        Player player = playerIdMap.get(playerId);
        if (player == null) {
            return;
        }

        if (player.getAvatar().isEmpty()) {
            return;
        }

        assignedAvatarRepository.removeSelectedAvatar(playerId);
        player.clearAvatar();
        markDirty(player);
    }

    public Player getOnlinePlayer(int playerId) {
        Player player = playerIdMap.get(playerId);
        if (player == null) {
            throw new IllegalArgumentException("Player is not online");
        }
        return player;
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
        broadcastService.broadcast(new SocialMessage.PlayerInfoList(playerInfos));

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
