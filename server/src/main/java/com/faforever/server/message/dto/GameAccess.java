package com.faforever.server.message.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum GameAccess {
    @JsonProperty("public") PUBLIC,
    @JsonProperty("password") PASSWORD
}
