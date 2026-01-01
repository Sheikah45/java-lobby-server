package com.faforever.server.message.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonFormat(shape = JsonFormat.Shape.NUMBER_INT)
public enum LobbyMode {
    @JsonProperty(index = 0) DEFAULT_LOBBY,
    @JsonProperty(index = 1) AUTO_LOBBY
}
