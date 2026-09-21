package com.faforever.server.game;

import com.faforever.server.message.MessageBroker;
import com.faforever.server.message.external.GPGMessage;
import com.faforever.server.message.internal.InboundLobbyMessage;
import com.faforever.server.message.internal.OutboundLobbyMessage;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;

import java.util.List;

@JBossLog
@RequiredArgsConstructor
@ApplicationScoped
public class GPGService {

    private final GameService gameService;

    private final MessageBroker messageBroker;

    public void handleRequest(InboundLobbyMessage<GPGMessage.Client> request) {
        long sessionId = request.sessionId();
        int playerId = request.playerId();
        Game game = gameService.getSessionGame(sessionId);
        boolean isHost = game.getHost().getId() == playerId;
        switch (request.message()) {
            case GPGMessage.AIOption aiOption when isHost -> {
                game.addAiOption(
                        aiOption.aiName(),
                        aiOption.optionKey(),
                        aiOption.optionValue()
                );
                gameService.markDirty(game);

            }
            case GPGMessage.ClearSlot clearSlot when isHost -> {
                int slot = Integer.parseInt(clearSlot.args().getFirst().toString());
                //        game.clearSlot(slot);
            }
            case GPGMessage.EnforceRating _ when isHost -> {
                game.setEnforceRatingRange(true);
                gameService.markDirty(game);
            }
            case GPGMessage.PlayerOption playerOption when isHost -> {
                int optionPlayerId = Integer.parseInt(playerOption.args().getFirst().toString());
                String key = playerOption.args().get(1).toString();
                Object value = playerOption.args().get(2);

                game.addPlayerOption(optionPlayerId, key, value);
                gameService.markDirty(game);
            }
            case GPGMessage.GameOption gameOption when isHost -> {
                game.addOption(
                        gameOption.args().get(0).toString(),
                        gameOption.args().get(1)
                );
                gameService.markDirty(game);
            }
            case GPGMessage.GameMods gameMods when isHost -> {
                String mode = gameMods.args().getFirst().toString();
                if ("activated".equals(mode)) {
                    int modCount = Integer.parseInt(gameMods.args().get(1).toString());
                    if (modCount == 0) {
                        //                game.clearMods();
                    }
                } else if ("uids".equals(mode)) {
                    String[] uids = gameMods.args().get(1).toString().split(" ");
                    //            game.setMods(Map.of(uids, "Unknown sim mod")); // Simplified - would fetch names from DB
                }
            }
            case GPGMessage.Client.HostOnly hostOnly ->
                    LOG.warnf("Host only message received from non host %s", hostOnly);
            case GPGMessage.Bottleneck bottleneck -> LOG.debugf("Bottleneck detected: %s", bottleneck.args());
            case GPGMessage.BottleneckCleared _ -> LOG.debug("Bottleneck cleared");
            case GPGMessage.Desync _ -> game.incrementDesyncs();
            case GPGMessage.Disconnected disconnected -> LOG.debugf("Player disconnected: %s", disconnected.args());
            case GPGMessage.GameEnded _ -> {
//        game.checkGameFinish(sessionController.getPlayer());
            }
            case GPGMessage.GameFull _ -> LOG.debug("Game is full");
            case GPGMessage.GameResult gameResult -> {
                int army = Integer.parseInt(gameResult.args().getFirst().toString());
                String result = gameResult.args().get(1).toString().toLowerCase();

                String[] parts = result.split(" ");
                if (parts.length >= 2) {
                    String resultType = parts[parts.length - 2];
                    int score = Integer.parseInt(parts[parts.length - 1]);

                    //            game.addResult(
                    //                sessionController.getPlayer().getId(),
                    //                army,
                    //                resultType,
                    //                score
                    //            );
                }
            }
            case GPGMessage.GameState gameState -> {
                String state = gameState.state();

                switch (state) {
                    case "Idle" -> {
                    }
                    case "Lobby" -> {
                        if (isHost && !game.isHosted()) {
                            messageBroker.handleOutboundMessage(OutboundLobbyMessage.forSession(sessionId, new GPGMessage.HostGame(
                                    List.of(game.getMapName()))));
                            game.markHosted();
                            gameService.markDirty(game);
                        }
                    }
                    case "Launching" -> {
                        //        if (!isHost() || game.getState() != GameState.LOBBY) {
                        //            return;
                        //        }
                        LOG.infof("Launching game %s", game);
                    }
                    case "Ended" -> {

                    }
                }
            }
            case GPGMessage.IceMsg iceMsg -> {
                int receiverId = Integer.parseInt(iceMsg.args().getFirst().toString());
                String iceMessage = iceMsg.args().get(1).toString();
                LOG.debugf("ICE message for player %d: %s", receiverId, iceMessage);
            }
            case GPGMessage.JsonStats jsonStats -> {
                String stats = jsonStats.args().getFirst().toString();
                //        try {
                //            game.reportArmyStats(stats);
                //        } catch (Exception e) {
                //            LOG.warnf("Failed to parse JSON stats: %s", e.getMessage());
                //        }
            }
            case GPGMessage.LaunchStatus launchStatus -> {
                String status = launchStatus.args().getFirst().toString();
                LOG.debugf("Launch status: %s", status);
            }
            case GPGMessage.OperationComplete operationComplete -> {
                boolean primary = "1".equals(operationComplete.args().getFirst().toString());
                boolean secondary = "1".equals(operationComplete.args().get(1).toString());
                String delta = operationComplete.args().get(2).toString();

                if (primary) {
                    LOG.debugf("Operation complete: primary=%s, secondary=%s, delta=%s",
                            primary, secondary, delta);
                }
            }
            case GPGMessage.Rehost _ -> LOG.debug("Game rehosted");
            case GPGMessage.TeamkillHappened teamkillHappened ->
                    LOG.debugf("Teamkill happened: %s", teamkillHappened.args());
            case GPGMessage.TeamkillReport teamkillReport -> LOG.debugf("Teamkill reported: %s", teamkillReport.args());
            case GPGMessage.Chat _ -> {}
            case GPGMessage.UnknownMessage unknownMessage ->
                    LOG.warnf("Received unknown GPG message: %s", unknownMessage.command());
        }
    }


}
