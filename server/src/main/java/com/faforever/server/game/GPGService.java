package com.faforever.server.game;

import com.faforever.server.connection.SessionController;
import com.faforever.server.message.GPGMessage;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;

import java.util.List;

@JBossLog
@RequiredArgsConstructor
@ApplicationScoped
public class GPGService {

    private final GameService gameService;

    private final SessionController sessionController;

    public void updateAiOption(GPGMessage.AIOption aiOptionMessage) {
        Game game = sessionController.getGame();
        if (!sessionController.getPlayer().equals(game.getHost())) {
            return;
        }

        game.addAiOption(aiOptionMessage.aiName(), aiOptionMessage.optionKey(), aiOptionMessage.optionValue());
        gameService.markDirty();
    }

    public void updateGameState(GPGMessage.GameState gameStateMessage) {
        Game game = sessionController.getGame();
        final boolean isHost = sessionController.getPlayer().equals(game.getHost());
        switch (gameStateMessage.state()) {
            case "Idle" -> {
                if (isHost) {
                    sessionController.setGame(game);
                }
            }

            case "Lobby" -> {
                if (isHost) {
                    sessionController.sendGpgHostGame();
                    game.markHosted();
                }
            }
            case "Launching" -> {}
            case "Ended" -> {}
        }
    }

}
