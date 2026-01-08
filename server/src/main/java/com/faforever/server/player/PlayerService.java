package com.faforever.server.player;

import com.faforever.server.broadcast.BroadcastService;
import com.faforever.server.connection.SessionController;
import com.faforever.server.message.SocialMessage;
import com.faforever.server.message.dto.DtoMapper;
import com.faforever.server.message.dto.PlayerInfo;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@JBossLog
@RequiredArgsConstructor
@ApplicationScoped
public class PlayerService {

    private final BroadcastService broadcastService;

    private final SessionController sessionController;

    private final PlayerRepository playerRepository;

    private final PlayerMapper playerMapper;
    private final DtoMapper dtoMapper;

    private final Map<Integer, Player> playerIdMap = new ConcurrentHashMap<>();

    private final Set<Player> dirtyPlayers = ConcurrentHashMap.newKeySet();

    @Transactional
    public void initializeSessionPlayer(int playerId) {
        Player player = playerIdMap.computeIfAbsent(playerId, this::initializePlayer);
        sessionController.setPlayer(player);
        sessionController.broadcast(new SocialMessage.PlayerInfoList(dtoMapper.map(playerIdMap.values())));
        dirtyPlayers.add(player);
    }

    public void kickPlayer(int playerId) {
        Player player = playerIdMap.get(playerId);
        if (player == null) {
            return;
        }

        player.kick();
    }

    private Player initializePlayer(int playerId) {
        return playerRepository.findByIdOptional(playerId).map(playerMapper::map).orElseThrow();
    }

    @RunOnVirtualThread
    @Scheduled(every = "1s", skipExecutionIf = Scheduled.ApplicationNotRunning.class, concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
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
    @Scheduled(every = "1s", skipExecutionIf = Scheduled.ApplicationNotRunning.class, concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void removeDisconnectedPlayers() {
        Collection<Player> playersValuesView = playerIdMap.values();
        Set<Player> disconnectedPlayers = new HashSet<>(playersValuesView);
        playersValuesView.removeIf(player -> !player.isConnected());
        disconnectedPlayers.removeAll(playersValuesView);
        dirtyPlayers.addAll(disconnectedPlayers);
    }
}
