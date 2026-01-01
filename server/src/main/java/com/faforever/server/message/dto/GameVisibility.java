package com.faforever.server.message.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum GameVisibility {
    @JsonProperty("public") PUBLIC,
    @JsonProperty("friends") PRIVATE
}
