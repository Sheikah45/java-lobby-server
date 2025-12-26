package com.faforever.server.social;

import com.faforever.server.broadcast.BroadcastService;
import com.faforever.server.message.dto.PlayerInfo;
import com.faforever.server.message.SocialMessage;
import com.faforever.server.session.SessionController;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@JBossLog
@RequiredArgsConstructor
@ApplicationScoped
public class PlayerService {

    private final SessionController sessionController;
    private final BroadcastService broadcastService;
    
    private final Map<Integer, Player> playerIdMap = new ConcurrentHashMap<>();

    private final Set<Player> dirtyPlayers = ConcurrentHashMap.newKeySet();

    public void initializeSessionPlayer(int playerId) {
        Player player = playerIdMap.computeIfAbsent(playerId, id -> new Player(id, ""));
        sessionController.setPlayer(player);
    }

    @Scheduled(every = "1s", skipExecutionIf = Scheduled.ApplicationNotRunning.class, concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void updateDirtyPlayers() {
        Set<Player> frozenDirtyPlayers = Set.copyOf(dirtyPlayers);
        if (frozenDirtyPlayers.isEmpty()) {
            return;
        }

        Set<PlayerInfo> playerInfos = frozenDirtyPlayers.stream().map(Player::asPlayerInfo).collect(Collectors.toSet());
        broadcastService.broadcast(new SocialMessage.PlayerInfoList(playerInfos));



        dirtyPlayers.removeAll(frozenDirtyPlayers);
    }
}
