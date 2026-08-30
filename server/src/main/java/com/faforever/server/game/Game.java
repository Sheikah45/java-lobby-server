package com.faforever.server.game;

import com.faforever.server.connection.SessionController;
import com.faforever.server.message.dto.GameType;
import com.faforever.server.message.dto.GameVisibility;
import com.faforever.server.player.Player;
import jakarta.enterprise.context.Dependent;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
@Dependent
public class Game {

    private final GameService gameService;

    @Getter
    @Setter(AccessLevel.PACKAGE)
    private Details details;

    private int desyncs = 0;

    private final Map<Integer, SessionController> playerGameConnectionMap = new ConcurrentHashMap<>();

    private final Map<String, Map<String, Object>> aiOptions = new ConcurrentHashMap<>();
    private final Map<Integer, Map<String, Object>> playerOptions = new ConcurrentHashMap<>();

    private final Map<String, Object> options = new ConcurrentHashMap<>();

    private OffsetDateTime hostedAt;
    private OffsetDateTime launchedAt;
    private int maxPlayers;

    @Getter
    private boolean enforceRatingRange;
    @Getter
    private String mapName;

    public void setHostedAt(OffsetDateTime hostedAt) {
        this.hostedAt = hostedAt;
        gameService.markDirty(this);
    }

    public void setLaunchedAt(OffsetDateTime launchedAt) {
        this.launchedAt = launchedAt;
        gameService.markDirty(this);
    }

    public void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers;
        gameService.markDirty(this);
    }

    public void setMapName(String mapName) {
        this.mapName = mapName;
        gameService.markDirty(this);
    }

    public void setEnforceRatingRange(boolean enforceRatingRange) {
        this.enforceRatingRange = enforceRatingRange;
        gameService.markDirty(this);
    }

    public void addGameConnection(SessionController sessionController) {
        SessionController existingConnection = playerGameConnectionMap.putIfAbsent(sessionController.player().orElseThrow().getDetails().id(),
                sessionController);
        if (existingConnection != null) {
            throw new IllegalStateException("Game connection for player already exists");
        }
        gameService.markDirty(this);
    }

    public void removeGameConnection(int playerId) {
        SessionController sessionController = playerGameConnectionMap.remove(playerId);
        sessionController.clearGame();
    }

    void addAiOption(String aiName, String optionKey, Object optionValue) {
        aiOptions.computeIfAbsent(aiName, _ -> new ConcurrentHashMap<>()).put(optionKey, optionValue);
        gameService.markDirty(this);
    }

    void addPlayerOption(Integer playerId, String optionKey, Object optionValue) {
        playerOptions.computeIfAbsent(playerId, _ -> new ConcurrentHashMap<>()).put(optionKey, optionValue);
        gameService.markDirty(this);
    }

    void addOption(String optionKey, Object optionValue) {
        options.put(optionKey, optionValue);
        gameService.markDirty(this);
    }

    void markHosted() {
        if (hostedAt == null) {
            hostedAt = OffsetDateTime.now();
            playerGameConnectionMap.get(details.host.getDetails().id()).sendGpgHostGame();
            gameService.markDirty(this);
        }
    }

    void incrementDesyncs() {
        desyncs++;
    }

    public record Details(
            int id,
            GameVisibility visibility,
            @Nullable String password,
            String title,
            String ratingType,
            GameType gameType,
            String featuredMod,
            Player host,
            @Nullable Integer ratingMax,
            @Nullable Integer ratingMin
    ) {}
}
