package com.faforever.server.message.client.dto;

public record VetoData(
        int mapPoolMapVersionId,
        int vetoTokensApplied,
        int matchmakerQueueMapPoolId
) {
}
