package com.faforever.server.game;

import com.faforever.server.connection.SessionController;
import com.faforever.server.message.dto.GameType;
import com.faforever.server.message.dto.GameVisibility;
import com.faforever.server.player.Player;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Game {

    @Getter
    private final int id;
    @Getter
    private final GameVisibility visibility;
    @Getter
    private final @Nullable String password;
    @Getter
    private final String title;
    @Getter
    private final String ratingType;
    @Getter
    private final GameType gameType;
    @Getter
    private final String featuredMod;
    @Getter
    private final Player host;
    @Getter
    private final @Nullable Integer ratingMax;
    @Getter
    private final @Nullable Integer ratingMin;
    @Getter
    private final boolean enforcedRatingRange;


    private final Map<Player, SessionController> playerGameConnectionMap = new ConcurrentHashMap<>();

    private final Map<String, Map<String, Object>> aiOptions = new ConcurrentHashMap<>();

    private OffsetDateTime hostedAt;
    private OffsetDateTime launchedAt;
    private int maxPlayers;
    @Getter
    @Setter(AccessLevel.PACKAGE)
    private String mapName;

    Game(int id, GameVisibility visibility, @Nullable String password, String title, String ratingType, GameType gameType,
         String featuredMod, Player host, @Nullable Integer ratingMax, @Nullable Integer ratingMin,
         boolean enforcedRatingRange) {
        this.id = id;
        this.visibility = visibility;
        this.password = password;
        this.title = title;
        this.ratingType = ratingType;
        this.gameType = gameType;
        this.featuredMod = featuredMod;
        this.host = host;
        this.ratingMax = ratingMax;
        this.ratingMin = ratingMin;
        this.enforcedRatingRange = enforcedRatingRange;
    }

    public void addGameConnection(SessionController sessionController) {
        SessionController existingConnection = playerGameConnectionMap.putIfAbsent(sessionController.getPlayer(), sessionController);
        if (existingConnection != null) {
            throw new IllegalStateException("Game connection for player already exists");
        }
    }

    void addAiOption(String aiName, String optionKey, Object optionValue) {
        aiOptions.computeIfAbsent(aiName, _ -> new ConcurrentHashMap<>()).put(optionKey, optionValue);
    }

    void markHosted() {
        if (hostedAt == null) {
            hostedAt = OffsetDateTime.now();
        }
    }
}
