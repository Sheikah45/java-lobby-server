package com.faforever.server.game;

import com.faforever.server.domain.FriendOrFoeEntity;
import com.faforever.server.exception.ClientException;
import com.faforever.server.message.client.GameMessage;
import com.faforever.server.message.client.LobbyMessage;
import com.faforever.server.message.client.dto.GameInfo;
import com.faforever.server.message.client.dto.GameType;
import com.faforever.server.message.client.dto.GameVisibility;
import com.faforever.server.message.service.InboundLobbyMessage;
import com.faforever.server.message.service.OutboundLobbyMessage;
import com.faforever.server.message.service.PlayerConnectionDetails;
import com.faforever.server.player.FriendOrFoeRepository;
import com.faforever.server.player.Player;
import com.faforever.server.player.PlayerRepository;
import com.faforever.server.rating.LeaderboardRating;
import com.faforever.server.rating.LeaderboardRatingRepository;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.jbosslog.JBossLog;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

@JBossLog
@ApplicationScoped
public class GameService {

    private final Emitter<OutboundLobbyMessage<LobbyMessage.Server>> messageBroker;

    private final LeaderboardRatingRepository leaderboardRatingRepository;
    private final FriendOrFoeRepository friendOrFoeRepository;
    private final GameRepository gameRepository;
    private final PlayerRepository playerRepository;

    private final Map<Long, Game> sessionGameMap = new ConcurrentHashMap<>();
    private final Map<Integer, Game> gameIdMap = new ConcurrentHashMap<>();

    private final Map<Integer, Player> playerIdMap = new ConcurrentHashMap<>();
    private final Map<Player, Set<Long>> playerSessionIdMap = new ConcurrentHashMap<>();
    private final Map<Long, Player> sessionPlayerIdMap = new ConcurrentHashMap<>();

    @Getter(AccessLevel.PACKAGE)
    private final Set<Game> dirtyGames = ConcurrentHashMap.newKeySet();

    private final AtomicInteger gameCounter = new AtomicInteger();
    private OffsetDateTime lastRatingUpdateTime = OffsetDateTime.now();

    GameService(@Channel("lobby-outbound") Emitter<OutboundLobbyMessage<LobbyMessage.Server>> messageBroker,
                LeaderboardRatingRepository leaderboardRatingRepository, FriendOrFoeRepository friendOrFoeRepository,
                GameRepository gameRepository, PlayerRepository playerRepository) {
        this.messageBroker = messageBroker;
        this.leaderboardRatingRepository = leaderboardRatingRepository;
        this.friendOrFoeRepository = friendOrFoeRepository;
        this.gameRepository = gameRepository;
        this.playerRepository = playerRepository;
    }

    @PostConstruct
    void initialize() {
        gameCounter.set(gameRepository.findMaxGameId());
    }

    @Incoming("player-connection")
    @RunOnVirtualThread
    void handlePlayerConnectionDetails(PlayerConnectionDetails playerConnectionDetails) {
        long sessionId = playerConnectionDetails.sessionId();
        int playerId = playerConnectionDetails.playerId();
        if (playerConnectionDetails.connected()) {
            Player player = playerIdMap.computeIfAbsent(playerId, playerRepository::loadPlayer);
            Player existingPlayer = sessionPlayerIdMap.putIfAbsent(sessionId, player);
            sendGamesToSession(sessionId);
        } else {
            sessionPlayerIdMap.remove(sessionId);
        }
    }

    @Incoming("game-inbound")
    public void handleRequest(InboundLobbyMessage<GameMessage.Client> request) {
        long sessionId = request.sessionId();
        switch (request.message()) {
            case GameMessage.HostGameRequest hostGameRequest -> hostGame(sessionId, hostGameRequest);
            case GameMessage.JoinGameRequest joinGameRequest -> joinGame(sessionId, joinGameRequest);
            case GameMessage.RestoreGameSessionRequest restoreGameSessionRequest ->
                    restoreGameSession(sessionId, restoreGameSessionRequest);
        }
    }

    Game getSessionGame(long sessionId) {
        Player player = sessionPlayerIdMap.get(sessionId);
        if (player == null) {
            throw new IllegalStateException("session not associated with a player");
        }
        return player.getGame().orElseThrow(() -> new IllegalStateException("session not associated with a game"));
    }

    void sendGamesToSession(long sessionId) {
        Set<Game> games = Set.copyOf(gameIdMap.values());
        Player player = sessionPlayerIdMap.get(sessionId);
        if (player == null) {
            return;
        }
        GameMessage.GameInfoList filteredGameInfoMessage = createFilteredGameInfoMessage(games, player);
        if (filteredGameInfoMessage.games().isEmpty()) {
            return;
        }
        messageBroker.send(OutboundLobbyMessage.forSession(sessionId, filteredGameInfoMessage));
    }

    void sendGamesToPlayer(int playerId) {
        Set<Game> games = Set.copyOf(gameIdMap.values());
        Player player = playerIdMap.get(playerId);
        if (player == null) {
            return;
        }

        GameMessage.GameInfoList filteredGameInfoMessage = createFilteredGameInfoMessage(games, player);
        if (filteredGameInfoMessage.games().isEmpty()) {
            return;
        }
        messageBroker.send(OutboundLobbyMessage.forPlayer(playerId, filteredGameInfoMessage));
    }

    private void hostGame(long sessionId, GameMessage.HostGameRequest hostMessage) {
        Player player = sessionPlayerIdMap.get(sessionId);
        if (player == null) {
            throw new IllegalStateException("session not associated with a player");
        }

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
        messageBroker.send(OutboundLobbyMessage.forSession(sessionId, GameMessage.GameLaunchResponse.from(game)));

        markDirty(game);
    }

    private void joinGame(long sessionId, GameMessage.JoinGameRequest joinMessage) {
        Player player = sessionPlayerIdMap.get(sessionId);
        if (player == null) {
            throw new IllegalStateException("session not associated with a player");
        }

        int gameId = joinMessage.gameId();
        Game game = gameIdMap.get(gameId);
        if (game == null) {
            messageBroker.send(OutboundLobbyMessage.forSession(sessionId,
                    new GameMessage.GameJoinFailed("host_left_game", gameId)));
            return;
        }

        if (game.getState() != Game.State.LOBBY) {
            messageBroker.send(OutboundLobbyMessage.forSession(sessionId,
                    new GameMessage.GameJoinFailed("game_not_ready", gameId)));
            return;
        }

        if (!game.getPassword().map(password -> password.equals(joinMessage.password())).orElse(true)) {
            messageBroker.send(
                    OutboundLobbyMessage.forSession(sessionId, new GameMessage.GameJoinFailed("bad_password", gameId)));
            return;
        }

        if (!Set.of(GameType.CUSTOM, GameType.COOP).contains(game.getGameType())) {
            throw new ClientException("Game cannot be joined");
        }

        if (!isGameVisibleToPlayer(game, player)) {
            throw new ClientException("You cannot join this game");
        }

        player.setGame(game);

        messageBroker.send(OutboundLobbyMessage.forSession(sessionId, GameMessage.GameLaunchResponse.from(game)));

        markDirty(game);
    }

    private void restoreGameSession(long sessionId, GameMessage.RestoreGameSessionRequest restoreMessage) {
        Player player = sessionPlayerIdMap.get(sessionId);
        if (player == null) {
            throw new IllegalStateException("session not associated with a player");
        }

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
    @Scheduled(every = "1m", skipExecutionIf = Scheduled.ApplicationNotRunning.class, concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void updatePlayerLeaderboardRatings() {
        OffsetDateTime currentRatingUpdateTime = lastRatingUpdateTime;
        lastRatingUpdateTime = OffsetDateTime.now();
        Map<Integer, Set<LeaderboardRating>> playerIdRatingMap = leaderboardRatingRepository.loadUpdatedLeaderboardRatings(
                Set.copyOf(playerIdMap.keySet()), currentRatingUpdateTime);

        playerIdRatingMap.forEach((playerId, newLeaderboardRatings) -> {
            Player player = playerIdMap.get(playerId);
            if (player == null) {
                return;
            }

            player.updateLeaderboardRatings(newLeaderboardRatings);
            sendGamesToPlayer(playerId);
        });
    }

    @RunOnVirtualThread
    @Scheduled(every = "1m", skipExecutionIf = Scheduled.ApplicationNotRunning.class, concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void updateHostFriendAndFoes() {
        Map<Integer, Game> hostGames = gameIdMap.values()
                                                .stream()
                                                .collect(Collectors.toMap(game -> game.getHost().getId(),
                                                        Function.identity()));
        Map<Integer, Map<FriendOrFoeEntity.Status, Set<Integer>>> hostFriendOrFoes = friendOrFoeRepository.loadPlayerFriendOrFoes(
                hostGames.keySet());

        hostFriendOrFoes.forEach((playerId, friendOrFoes) -> {
            Game game = hostGames.get(playerId);
            if (game == null) {
                return;
            }

            Player player = playerIdMap.get(playerId);
            if (player == null) {
                return;
            }

            player.setFoes(friendOrFoes.getOrDefault(FriendOrFoeEntity.Status.FOE, Set.of()));
            player.setFriends(friendOrFoes.getOrDefault(FriendOrFoeEntity.Status.FRIEND, Set.of()));

            markDirty(game);
        });
    }

    @RunOnVirtualThread
    @Scheduled(every = "1s", skipExecutionIf = Scheduled.ApplicationNotRunning.class, concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void updateDirtyGames() {
        Set<Game> frozenDirtyGames = Set.copyOf(dirtyGames);
        if (frozenDirtyGames.isEmpty()) {
            return;
        }

        playerIdMap.values().forEach(player -> {
            GameMessage.GameInfoList filteredGameInfoMessage = createFilteredGameInfoMessage(frozenDirtyGames, player);
            if (filteredGameInfoMessage.games().isEmpty()) {
                return;
            }
            messageBroker.send(OutboundLobbyMessage.forPlayer(player.getId(), filteredGameInfoMessage));
        });

        dirtyGames.removeAll(frozenDirtyGames);
    }

    private GameMessage.GameInfoList createFilteredGameInfoMessage(Set<Game> games, Player player) {
        Set<GameInfo> visibleGames = games.stream()
                                          .filter(game -> isGameVisibleToPlayer(game, player))
                                          .map(GameInfo::from)
                                          .collect(Collectors.toSet());
        return new GameMessage.GameInfoList(visibleGames);
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
