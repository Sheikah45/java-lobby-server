package com.faforever.server.game;

import com.faforever.server.connection.SessionController;
import com.faforever.server.exception.ClientException;
import com.faforever.server.message.GameMessage;
import com.faforever.server.message.SocialMessage;
import com.faforever.server.message.dto.DtoMapper;
import com.faforever.server.message.dto.GameType;
import com.faforever.server.message.dto.GameVisibility;
import com.faforever.server.message.dto.PlayerInfo;
import com.faforever.server.player.Player;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@JBossLog
@RequiredArgsConstructor
@ApplicationScoped
public class GameService {

    private final GameRepository gameRepository;

    private final DtoMapper dtoMapper;

    private final SessionController sessionController;

    private final Map<Integer, Game> gameIdMap = new ConcurrentHashMap<>();

    private final Set<Game> dirtyGames = ConcurrentHashMap.newKeySet();

    private final AtomicInteger gameCounter = new AtomicInteger();

    @PostConstruct
    void initialize() {
        gameCounter.set(gameRepository.findMaxGameId());
    }

    @Transactional
    public void handleHostRequest(GameMessage.HostGameRequest hostMessage) {
        String title = hostMessage.title();
        if (title.isBlank()) {
            throw new ClientException("Title must not be empty");
        }

        if (!StandardCharsets.US_ASCII.newEncoder().canEncode(title)) {
            throw new ClientException("Title must contain only ascii characters");
        }

        String featuredMod = hostMessage.featuredMod();
        if (featuredMod == null) {
            featuredMod = "faf";
        }

        String mapName = hostMessage.mapName();
        if (mapName == null) {
            mapName = "scmp_007";
        }

        GameVisibility visibility = hostMessage.visibility();
        Integer ratingMax = hostMessage.ratingMax();
        Integer ratingMin = hostMessage.ratingMin();
        boolean enforceRatingRange = hostMessage.enforceRatingRange();

        int gameId = gameCounter.incrementAndGet();

        Player player = sessionController.getPlayer();
        Game game = new Game(gameId, visibility, hostMessage.password(), title, "global", GameType.CUSTOM, featuredMod,
                player, ratingMax, ratingMin, enforceRatingRange);

        gameIdMap.put(gameId, game);

        sessionController.launchGame(game);

        dirtyGames.add(game);
    }

    void markDirty() {
        dirtyGames.add(sessionController.getGame());
    }

    @RunOnVirtualThread
    @Scheduled(every = "1s", skipExecutionIf = Scheduled.ApplicationNotRunning.class, concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void updateDirtyGames() {
        Set<Game> frozenDirtyGames = Set.copyOf(dirtyGames);
        if (frozenDirtyGames.isEmpty()) {
            return;
        }

        dirtyGames.removeAll(frozenDirtyGames);
    }
}
