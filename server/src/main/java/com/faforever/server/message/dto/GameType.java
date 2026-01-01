package com.faforever.server.message.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum GameType {
    @JsonProperty("unknown") UNKNOWN,
    @JsonProperty("custom") CUSTOM,
    @JsonProperty("tournament") TOURNAMENT,
    @JsonProperty("matchmaker") MATCHMAKER,
    @JsonProperty("coop") COOP,
    @JsonProperty("tutorial") TUTORIAL,
}
