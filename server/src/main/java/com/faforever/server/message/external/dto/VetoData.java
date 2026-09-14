package com.faforever.server.message.external.dto;

public record VetoData(
        int mapPoolMapVersionId,
        int vetoTokensApplied,
        int matchmakerQueueMapPoolId
) {
}
