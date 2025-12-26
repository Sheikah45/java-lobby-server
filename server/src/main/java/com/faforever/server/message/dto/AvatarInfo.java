package com.faforever.server.message.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.net.URI;

public record AvatarInfo(
        URI url,
        @JsonProperty("tooltip") String description
) {
}
