package com.faforever.server.message.external.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AvatarInfo(
        String url,
        @JsonProperty("tooltip") String description
) {
}
