package com.faforever.server.message.dto;

import java.time.OffsetDateTime;

public record MatchmakerQueue(
        String queueName,
        OffsetDateTime popTime,
        float secondsUntilPop,
        int teamSize,
        int numberOfPlayers
) {
}
