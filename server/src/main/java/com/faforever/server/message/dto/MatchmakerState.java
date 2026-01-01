package com.faforever.server.message.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum MatchmakerState {
    @JsonProperty("start") START,
    @JsonProperty("stop") STOP
}
