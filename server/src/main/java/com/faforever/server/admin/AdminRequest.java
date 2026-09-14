package com.faforever.server.admin;

public sealed interface AdminRequest {

    long requestorSessionId();

    record Broadcast(long requestorSessionId, String message) implements AdminRequest {}
    record KickPlayer(long requestorSessionId, int playerId) implements AdminRequest {}
    record ClosePlayerGame(long requestorSessionId, int playerId) implements AdminRequest {}
}
