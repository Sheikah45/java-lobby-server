package com.faforever.server.message.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonFormat(shape = JsonFormat.Shape.NUMBER_INT)
public enum VictoryCondition {
    @JsonProperty(index = 0) DEMORALIZATION,
    @JsonProperty(index = 1) DOMINATION,
    @JsonProperty(index = 2) ERADICATION,
    @JsonProperty(index = 3) SANDBOX,
}
