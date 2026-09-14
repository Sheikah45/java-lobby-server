package com.faforever.server.game;

import com.faforever.server.message.SessionHandler;
import com.faforever.server.message.external.GPGMessage;
import com.faforever.server.message.external.dto.GameType;
import com.faforever.server.message.external.dto.GameVisibility;
import com.faforever.server.player.Player;
import com.faforever.server.rating.Leaderboard;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public class Game {

    @Getter
    private final int id;
    @Getter
    private final GameVisibility visibility;
    @Getter
    private final @Nullable String password;
    @Getter
    private final Leaderboard leaderboard;
    @Getter
    private final GameType gameType;
    @Getter
    private final String featuredMod;
    @Getter
    private final Player host;
    @Getter
    private final RatingRange ratingRange;

    private final LongAdder desyncs = new LongAdder();

    private final Map<Player, SessionHandler> playerGameConnectionMap = new ConcurrentHashMap<>();

    private final Map<String, Map<String, Object>> aiOptions = new ConcurrentHashMap<>();
    private final Map<Integer, Map<String, Object>> playerOptions = new ConcurrentHashMap<>();

    private final Map<String, Object> options = new ConcurrentHashMap<>();

    @Setter
    private @Nullable OffsetDateTime hostedAt;
    @Setter
    private @Nullable OffsetDateTime launchedAt;
    private int maxPlayers;

    @Getter
    @Setter
    private boolean enforceRatingRange;
    @Getter
    @Setter
    private String title;
    @Getter
    @Setter
    private String mapName;

    public Game(int id, GameVisibility visibility, @Nullable String password, String ratingType, GameType gameType,
                String featuredMod, Player host, String title, String mapName, @Nullable Integer ratingMax, @Nullable Integer ratingMin,
                boolean enforceRatingRange) {
        this.id = id;
        this.visibility = visibility;
        this.password = password;
        this.leaderboard = new Leaderboard(ratingType);
        this.gameType = gameType;
        this.featuredMod = featuredMod;
        this.host = host;
        this.ratingRange = new RatingRange(ratingMin, ratingMax);
        this.enforceRatingRange = enforceRatingRange;
        this.title = title;
        this.mapName = mapName;
    }

    void addAiOption(String aiName, String optionKey, Object optionValue) {
        aiOptions.computeIfAbsent(aiName, _ -> new ConcurrentHashMap<>()).put(optionKey, optionValue);
    }

    void addPlayerOption(Integer playerId, String optionKey, Object optionValue) {
        playerOptions.computeIfAbsent(playerId, _ -> new ConcurrentHashMap<>()).put(optionKey, optionValue);
    }

    void addOption(String optionKey, Object optionValue) {
        options.put(optionKey, optionValue);
    }

    void markHosted() {
        if (hostedAt == null) {
            hostedAt = OffsetDateTime.now();
            SessionHandler hostSessionHandler = playerGameConnectionMap.get(host.getId());
            if (hostSessionHandler == null) {
                throw new IllegalStateException("Host connection does not exist");
            }
            hostSessionHandler.sendMessage(new GPGMessage.HostGame(
                    List.of(mapName)));
        }
    }

    void incrementDesyncs() {
        desyncs.increment();
    }
}
