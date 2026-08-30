package com.faforever.server.game;

import com.faforever.server.broadcast.BroadcastService;
import com.faforever.server.exception.ClientException;
import com.faforever.server.message.GameMessage;
import com.faforever.server.message.dto.DtoMapper;
import com.faforever.server.message.dto.GameType;
import com.faforever.server.message.dto.GameVisibility;
import com.faforever.server.player.Player;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@JBossLog
@RequiredArgsConstructor
@ApplicationScoped
public class GameService {

    private final BroadcastService broadcastService;

    private final GameRepository gameRepository;

    private final DtoMapper dtoMapper;

    private final Instance<Game> gameInstances;

    private final Map<Integer, Game> gameIdMap = new ConcurrentHashMap<>();

    private final Set<Game> dirtyGames = ConcurrentHashMap.newKeySet();

    private final AtomicInteger gameCounter = new AtomicInteger();

    @PostConstruct
    void initialize() {
        gameCounter.set(gameRepository.findMaxGameId());
    }

    public Game getGame(int gameId) {
        Game game = gameIdMap.get(gameId);
        if (game == null) {
            throw new IllegalArgumentException("Game with id " + gameId + " does not exist");
        }
        return game;
    }

    public Game createNewGame(Player host, GameMessage.HostGameRequest hostMessage) {
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

        Game game = gameInstances.get();
        game.setDetails(new Game.Details(gameId, visibility, hostMessage.password(), title, "global", GameType.CUSTOM, featuredMod,
                host, ratingMax, ratingMin));
        game.setMapName(mapName);
        game.setEnforceRatingRange(enforceRatingRange);

        gameIdMap.put(gameId, game);

        markDirty(game);

        return game;
    }

    void markDirty(Game game) {
        dirtyGames.add(game);
    }

    @RunOnVirtualThread
    @Scheduled(every = "1s", skipExecutionIf = Scheduled.ApplicationNotRunning.class, concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void updateDirtyGames() {
        Set<Game> frozenDirtyGames = Set.copyOf(dirtyGames);
        if (frozenDirtyGames.isEmpty()) {
            return;
        }

        //TODO: Hide games players shouldn't see
        broadcastService.broadcast(new GameMessage.GameInfoList(dtoMapper.mapGames(frozenDirtyGames)));

        dirtyGames.removeAll(frozenDirtyGames);
    }
}
