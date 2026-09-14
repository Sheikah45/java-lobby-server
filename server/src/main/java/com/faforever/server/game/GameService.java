package com.faforever.server.game;

import com.faforever.server.exception.ClientException;
import com.faforever.server.message.external.GameMessage;
import com.faforever.server.message.external.dto.DtoMapper;
import com.faforever.server.message.external.dto.GameType;
import com.faforever.server.message.external.dto.GameVisibility;
import com.faforever.server.player.Player;
import com.faforever.server.player.PlayerService;
import com.faforever.server.rating.LeaderboardRating;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@JBossLog
@RequiredArgsConstructor
@ApplicationScoped
public class GameService {

    private final PlayerService playerService;

    private final GameRepository gameRepository;

    private final DtoMapper dtoMapper;

    private final Map<Long, Game> sessionGameMap = new ConcurrentHashMap<>();
    private final Map<Integer, Game> gameIdMap = new ConcurrentHashMap<>();

    private final Set<Game> dirtyGames = ConcurrentHashMap.newKeySet();

    private final AtomicInteger gameCounter = new AtomicInteger();

    @PostConstruct
    void initialize() {
        gameCounter.set(gameRepository.findMaxGameId());
    }

    public void closePlayerGame(int playerId) {

    }

    public Game getSessionGame(long sessionId) {
        Game game = sessionGameMap.get(sessionId);
        if (game == null) {
            throw new IllegalArgumentException("Session id %d not associated with a game");
        }
        return game;
    }

    public Game hostGame(long sessionId, GameMessage.HostGameRequest hostMessage) {
        Player host = playerService.getSessionPlayer(sessionId);

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

        Game game = new Game(gameId, visibility, hostMessage.password(), "global", GameType.CUSTOM, featuredMod, host,
                title, mapName, ratingMax, ratingMin, enforceRatingRange);

        sessionGameMap.put(sessionId, game);
        gameIdMap.put(gameId, game);

        markDirty(game);

        return game;
    }

    void markDirty(Game game) {
        dirtyGames.add(game);
    }

    @RunOnVirtualThread
    @Scheduled(every = "1s", skipExecutionIf = Scheduled.ApplicationNotRunning.class,
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void updateDirtyGames() {
        Set<Game> frozenDirtyGames = Set.copyOf(dirtyGames);
        if (frozenDirtyGames.isEmpty()) {
            return;
        }

        playerService.broadcast(player -> createFilteredGameInfoMessage(frozenDirtyGames, player));

        dirtyGames.removeAll(frozenDirtyGames);
    }

    private GameMessage.GameInfoList createFilteredGameInfoMessage(Set<Game> games, Player player) {
        Set<Game> visibleGames = games.stream()
                                      .filter(game -> isGameVisibleToPlayer(game, player))
                                      .collect(Collectors.toSet());
        return new GameMessage.GameInfoList(dtoMapper.mapGames(visibleGames));
    }

    private boolean isGameVisibleToPlayer(Game game, Player player) {
        if (player.getGame().map(game::equals).orElse(false)) {
            return true;
        }

        Player host = game.getHost();
        if (switch (game.getVisibility()) {
            case PUBLIC -> host.isFoe(player.getId());
            case PRIVATE -> !host.isFriend(player.getId());
        }) {
            return false;
        }

        //TODO: check if 0 is appropriate
        return !game.isEnforceRatingRange() || game.getRatingRange()
                                                   .inRange(player.getRating(game.getLeaderboard())
                                                                  .map(LeaderboardRating::displayedRating)
                                                                  .orElse(0d));
    }
}
