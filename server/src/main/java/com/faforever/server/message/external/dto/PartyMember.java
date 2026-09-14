package com.faforever.server.message.external.dto;

import com.faforever.server.message.external.Faction;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Set;

public record PartyMember(
        @JsonProperty("player") int playerId,
        Set<Faction> factions
) {
}
