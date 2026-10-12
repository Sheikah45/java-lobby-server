package com.faforever.server.message.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum GameStatus {
    @JsonProperty("unknown") UNKNOWN,
    @JsonProperty("playing") PLAYING,
    @JsonProperty("open") OPEN,
    @JsonProperty("closed") CLOSED
}
