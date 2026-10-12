package com.faforever.server.game;

import com.faforever.server.exception.ClientException;
import com.faforever.server.message.client.GameMessage;
import com.faforever.server.message.client.LobbyMessage;
import com.faforever.server.message.client.dto.DtoMapper;
import com.faforever.server.message.client.dto.GameType;
import com.faforever.server.message.client.dto.GameVisibility;
import com.faforever.server.message.service.InboundLobbyMessage;
import com.faforever.server.message.service.OutboundLobbyMessage;
import com.faforever.server.player.Player;
import com.faforever.server.player.PlayerService;
import com.faforever.server.rating.LeaderboardRating;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.smallrye.reactive.messaging.ChannelRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@JBossLog
@ApplicationScoped
public class GameService {

    private final PlayerService playerService;

    private final Emitter<OutboundLobbyMessage<LobbyMessage.Server>> messageBroker;

    private final GameRepository gameRepository;

    private final DtoMapper dtoMapper;

    private final Map<Long, Game> sessionGameMap = new ConcurrentHashMap<>();
    private final Map<Integer, Game> gameIdMap = new ConcurrentHashMap<>();

    @Getter(AccessLevel.PACKAGE)
    private final Set<Game> dirtyGames = ConcurrentHashMap.newKeySet();

    private final AtomicInteger gameCounter = new AtomicInteger();

    public GameService(PlayerService playerService, @Channel("lobby-outbound") Emitter<OutboundLobbyMessage<LobbyMessage.Server>> messageBroker,
                       GameRepository gameRepository, DtoMapper dtoMapper) {
        this.playerService = playerService;
        this.messageBroker = messageBroker;
        this.gameRepository = gameRepository;
        this.dtoMapper = dtoMapper;
    }

    @PostConstruct
    void initialize() {
        gameCounter.set(gameRepository.findMaxGameId());
    }

    public void handleRequest(InboundLobbyMessage<GameMessage.Client> request) {
        long sessionId = request.sessionId();
        switch (request.message()) {
            case GameMessage.HostGameRequest hostGameRequest -> hostGame(sessionId, hostGameRequest);
            case GameMessage.JoinGameRequest joinGameRequest -> joinGame(sessionId, joinGameRequest);
            case GameMessage.RestoreGameSessionRequest restoreGameSessionRequest -> restoreGameSession(sessionId, restoreGameSessionRequest);
        }
    }

    public Game getSessionGame(long sessionId) {
        return playerService.getSessionPlayer(sessionId)
                            .getGame()
                            .orElseThrow(() -> new IllegalStateException("session not associated with a game"));
    }

    public void sendGamesToSession(long sessionId) {
        Set<Game> games = Set.copyOf(gameIdMap.values());
        Player player = playerService.getSessionPlayer(sessionId);
        GameMessage.GameInfoList filteredGameInfoMessage = createFilteredGameInfoMessage(games, player);
        if (filteredGameInfoMessage.games().isEmpty()) {
            return;
        }
        messageBroker.send(OutboundLobbyMessage.forSession(sessionId, filteredGameInfoMessage));
    }

    private void hostGame(long sessionId, GameMessage.HostGameRequest hostMessage) {
        Player player = playerService.getSessionPlayer(sessionId);

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

        Game game = new Game(gameId, visibility, hostMessage.password(), "global", GameType.CUSTOM, featuredMod, player,
                title, mapName, ratingMax, ratingMin, enforceRatingRange);

        gameIdMap.put(gameId, game);
        player.setGame(game);

        LOG.debug("Launching game");
        messageBroker.send(
                OutboundLobbyMessage.forSession(sessionId, dtoMapper.mapToGameLaunch(game)));

        markDirty(game);
    }

    private void joinGame(long sessionId, GameMessage.JoinGameRequest joinMessage) {
        Player player = playerService.getSessionPlayer(sessionId);

        int gameId = joinMessage.gameId();
        Game game = gameIdMap.get(gameId);
        if (game == null) {
            messageBroker.send(OutboundLobbyMessage.forSession(sessionId, new GameMessage.GameJoinFailed("host_left_game", gameId)));
            return;
        }

        if (game.getState() != Game.State.LOBBY) {
            messageBroker.send(OutboundLobbyMessage.forSession(sessionId, new GameMessage.GameJoinFailed("game_not_ready", gameId)));
            return;
        }

        if (!game.getPassword().map(password -> password.equals(joinMessage.password())).orElse(true)) {
            messageBroker.send(OutboundLobbyMessage.forSession(sessionId, new GameMessage.GameJoinFailed("bad_password", gameId)));
            return;
        }

        if (!Set.of(GameType.CUSTOM, GameType.COOP).contains(game.getGameType())) {
            throw new ClientException("Game cannot be joined");
        }

        if (!isGameVisibleToPlayer(game, player)) {
            throw new ClientException("You cannot join this game");
        }

        player.setGame(game);

        messageBroker.send(
                OutboundLobbyMessage.forSession(sessionId, dtoMapper.mapToGameLaunch(game)));

        markDirty(game);
    }

    private void restoreGameSession(long sessionId, GameMessage.RestoreGameSessionRequest restoreMessage) {
        Player player = playerService.getSessionPlayer(sessionId);
        int gameId = restoreMessage.gameId();
        Game game = gameIdMap.get(gameId);
        if (game == null) {
            throw new ClientException("The game you were connected to no longer exists");
        }

        if (!Set.of(Game.State.LOBBY, Game.State.LIVE).contains(game.getState())) {
            throw new ClientException("The game you were connected to is no longer available");
        }

        player.setGame(game);
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

        playerService.getOnlinePlayers().forEach(player -> {
            GameMessage.GameInfoList filteredGameInfoMessage = createFilteredGameInfoMessage(frozenDirtyGames, player);
            if (filteredGameInfoMessage.games().isEmpty()) {
                return;
            }
            messageBroker.send(
                    OutboundLobbyMessage.forPlayer(player.getId(), filteredGameInfoMessage));
        });

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
