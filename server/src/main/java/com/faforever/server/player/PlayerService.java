package com.faforever.server.player;

import com.faforever.server.domain.FriendOrFoeEntity;
import com.faforever.server.message.MessageEmitter;
import com.faforever.server.message.external.ConnectionMessage;
import com.faforever.server.message.external.LobbyMessage;
import com.faforever.server.message.external.SocialMessage;
import com.faforever.server.message.external.dto.DtoMapper;
import com.faforever.server.message.external.dto.PlayerInfo;
import com.faforever.server.message.internal.MessageRequest;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;
import org.jspecify.annotations.Nullable;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@JBossLog
@ApplicationScoped
@RequiredArgsConstructor
public class PlayerService {

    private final MessageEmitter messageEmitter;

    private final PlayerRepository playerRepository;
    private final FriendOrFoeRepository friendOrFoeRepository;
    private final AssignedAvatarRepository assignedAvatarRepository;

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
            throw new IllegalStateException("Session already has an existing player of id %d".formatted(existingPlayer.getId()));
        }

        Set<String> channels = new HashSet<>();
        player.getClan().map("#%s_clan"::formatted).ifPresent(channels::add);

        messageEmitter.send(new MessageRequest.ForSession(sessionId,
                new ConnectionMessage.LoginSuccessResponse(dtoMapper.map(player), OffsetDateTime.now())));
        messageEmitter.send(new MessageRequest.ForSession(sessionId,
                new SocialMessage.SocialInfo(channels, player.getFriendIds(), player.getFoeIds())));
        messageEmitter.send(new MessageRequest.ForSession(sessionId,
                new SocialMessage.PlayerInfoList(dtoMapper.map(playerIdMap.values()))));

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

    public void changeSocialRelationship(SocialRequest.FriendOrFoe friendOrFoeRequest) {
        long sessionId = friendOrFoeRequest.sessionId();
        Player player = sessionPlayerMap.get(sessionId);
        if (player == null) {
            return;
        }
        int targetId = friendOrFoeRequest.targetId();
        switch (friendOrFoeRequest) {
            case SocialRequest.FriendOrFoe.Add(_, _, SocialRequest.FriendOrFoe.Status status) -> {
                FriendOrFoeEntity.Status entityStatus = switch (status) {
                    case FOE -> FriendOrFoeEntity.Status.FOE;
                    case FRIEND -> FriendOrFoeEntity.Status.FRIEND;
                };
                friendOrFoeRepository.upsertPlayerRelationship(player.getId(), targetId, entityStatus);
                switch (entityStatus) {
                    case FOE -> player.addFoe(targetId);
                    case FRIEND -> player.addFriend(targetId);
                }
            }
            case SocialRequest.FriendOrFoe.Remove _ -> {
                friendOrFoeRepository.deletePlayerRelationship(player.getId(), targetId);
                player.removeFriendOrFoe(targetId);
            }
        }
    }

    public void sendAvatars(SocialRequest.Avatars avatarsRequest) {
        long sessionId = avatarsRequest.sessionId();
        Player player = sessionPlayerMap.get(sessionId);
        if (player == null) {
            return;
        }
        Set<Avatar> avatars = assignedAvatarRepository.findAssignedAvatarsByPlayer(player.getId());
        SocialMessage.AvatarInfoList avatarInfoList = new SocialMessage.AvatarInfoList(dtoMapper.mapAvatars(avatars));
        messageEmitter.send(new MessageRequest.ForSession(sessionId, avatarInfoList));
    }

    public void selectAvatar(SocialRequest.SelectAvatar selectRequest) {
        long sessionId = selectRequest.sessionId();
        Player player = sessionPlayerMap.get(sessionId);
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

    public void removeAvatar(SocialRequest.RemoveAvatar removeRequest) {
        long sessionId = removeRequest.sessionId();
        Player player = sessionPlayerMap.get(sessionId);
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

    public boolean sessionLacksPermission(long sessionId, String permission) {
        Player player = sessionPlayerMap.get(sessionId);
        if (player == null) {
            throw new IllegalArgumentException("Session id %d not associated with a player".formatted(sessionId));
        }
        return playerRepository.playerLacksPermission(player.getId(), permission);
    }

    public void broadcast(LobbyMessage.Broadcast message) {
        broadcast(_ -> message);
    }

    public void broadcast(Function<Player, LobbyMessage.@Nullable Broadcast> messageFunction) {
        sessionPlayerMap.forEach((sessionId, player) -> {
            LobbyMessage.Broadcast message = messageFunction.apply(player);
            if (message == null) {
                return;
            }

            messageEmitter.send(new MessageRequest.ForSession(sessionId, message));
        });
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
        broadcast(new SocialMessage.PlayerInfoList(playerInfos));

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
