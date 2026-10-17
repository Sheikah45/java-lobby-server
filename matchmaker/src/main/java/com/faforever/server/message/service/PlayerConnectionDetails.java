package com.faforever.server.message.service;

public record PlayerSessionStatus(
        int playerId,
        long sessionId,
        boolean connected
) {}
