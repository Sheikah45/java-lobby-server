package com.faforever.server.player;

import com.faforever.server.broadcast.BroadcastService;
import com.faforever.server.domain.AvatarEntity;
import com.faforever.server.domain.ClanEntity;
import com.faforever.server.domain.PlayerEntity;
import com.faforever.server.message.SocialMessage;
import com.faforever.server.message.dto.DtoMapper;
import com.faforever.server.message.dto.PlayerInfo;
import com.faforever.server.rating.Leaderboard;
import com.faforever.server.rating.LeaderboardRating;
import com.faforever.server.social.Avatar;
import com.faforever.server.social.FriendOrFoeRepository;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@JBossLog
@RequiredArgsConstructor
@ApplicationScoped
public class PlayerService {

    private final BroadcastService broadcastService;

    private final PlayerRepository playerRepository;
    private final FriendOrFoeRepository friendOrFoeRepository;

    private final DtoMapper dtoMapper;

    private final Instance<Player> playerInstances;

    private final Map<Integer, Player> playerIdMap = new ConcurrentHashMap<>();

    private final Set<Player> dirtyPlayers = ConcurrentHashMap.newKeySet();
    private final Set<Player> disconnectedPlayers = ConcurrentHashMap.newKeySet();

    @Transactional
    public Player getOrCreatePlayer(int playerId) {
        return playerIdMap.computeIfAbsent(playerId, this::initializePlayer);
    }

    public Set<Player> getOnlinePlayers() {
        return Set.copyOf(playerIdMap.values());
    }

    public void kickPlayer(int playerId) {
        Player player = playerIdMap.get(playerId);
        if (player == null) {
            return;
        }

        player.kick();
    }

    public void closePlayerGame(int playerId) {
        Player player = playerIdMap.get(playerId);
        if (player == null) {
            return;
        }

        player.closeGame();
    }

    void markDisconnected(Player player) {
        disconnectedPlayers.add(player);
    }

    void markDirty(Player player) {
        disconnectedPlayers.remove(player);
        dirtyPlayers.add(player);
    }

    private Player initializePlayer(int playerId) {
        PlayerEntity playerEntity = playerRepository.findByIdOptional(playerId).orElseThrow();
        Player player = playerInstances.get();
        ClanEntity clan = playerEntity.getClan();
        String clanTag = clan == null ? null : clan.getTag();
        player.setDetails(new Player.Details(playerId, playerEntity.getName(), clanTag));
        AvatarEntity avatar = playerEntity.getSelectedAvatar();
        if (avatar != null) {
            player.setAvatar(new Avatar(avatar.getUrl(), avatar.getDescription()));
        }
        Collection<LeaderboardRating> leaderboardRatings = playerEntity.getLeaderboardRatings()
                                                                       .stream()
                                                                       .map(leaderboardRatingEntity -> {
                                                                           Leaderboard leaderboard = new Leaderboard(
                                                                                   leaderboardRatingEntity.getLeaderboard()
                                                                                                          .getTechnicalName());
                                                                           return new LeaderboardRating(leaderboard,
                                                                                   leaderboardRatingEntity.getTotalGames(),
                                                                                   leaderboardRatingEntity.getMean(),
                                                                                   leaderboardRatingEntity.getDeviation());
                                                                       })
                                                                       .collect(Collectors.toSet());
        player.setLeaderboardRatings(leaderboardRatings);
        friendOrFoeRepository.stream("id.playerId", playerId).forEach(friendOrFoe -> {
            switch (friendOrFoe.getStatus()) {
                case FOE -> player.addFoe(friendOrFoe.getId().subjectId());
                case FRIEND -> player.addFriend(friendOrFoe.getId().subjectId());
            }
        });
        return player;
    }

    @RunOnVirtualThread
    @Scheduled(every = "1s", skipExecutionIf = Scheduled.ApplicationNotRunning.class,
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void updateDirtyPlayers() {
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

        frozenDisconnectedPlayers.stream().map(Player::getDetails).map(Player.Details::id).forEach(playerIdMap::remove);

        dirtyPlayers.addAll(frozenDisconnectedPlayers);
        disconnectedPlayers.removeAll(frozenDisconnectedPlayers);
    }
}
