package com.faforever.server.message.dto;

public record VetoData(
        int mapPoolMapVersionId,
        int vetoTokensApplied,
        int matchmakerQueueMapPoolId
) {
}
