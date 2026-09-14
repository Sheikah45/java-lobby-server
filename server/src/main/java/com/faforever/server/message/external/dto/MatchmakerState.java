package com.faforever.server.message.external.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum MatchmakerState {
    @JsonProperty("start") START,
    @JsonProperty("stop") STOP
}
