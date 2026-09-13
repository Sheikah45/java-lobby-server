package com.faforever.server.player;

import com.faforever.server.domain.AvatarEntity;

public record Avatar(
        String url,
        String description
) {

    public static Avatar from(AvatarEntity entity) {
        return new Avatar(entity.getUrl(), entity.getDescription());
    }
}
