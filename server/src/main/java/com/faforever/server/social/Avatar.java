package com.faforever.server.social;

import java.net.URI;

public record Avatar(
        URI url,
        String description
) {
}
