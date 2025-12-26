package com.faforever.server.session;

import org.jspecify.annotations.Nullable;

import java.io.Serializable;

public record UserAgent(
        @Nullable String agent,
        @Nullable String version
) implements Serializable {}
