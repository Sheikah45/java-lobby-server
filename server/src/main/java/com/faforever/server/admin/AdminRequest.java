package com.faforever.server.admin;

public sealed interface AdminRequest {

    int requestorId();

    record Broadcast(int requestorId, String message) implements AdminRequest {}
    record KickPlayer(int requestorId, int playerId) implements AdminRequest {}
    record ClosePlayerGame(int requestorId, int playerId) implements AdminRequest {}
}
