package com.faforever.server.message.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum GameVisibility {
    @JsonProperty("public") PUBLIC,
    @JsonProperty("friends") PRIVATE
}
