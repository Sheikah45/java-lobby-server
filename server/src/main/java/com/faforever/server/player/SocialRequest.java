package com.faforever.server.player;

public sealed interface SocialRequest {

    sealed interface FriendOrFoe extends SocialRequest {

        int playerId();

        int targetId();

        record Add(
                int playerId,
                int targetId,
                Status status
        ) implements FriendOrFoe {}

        record Remove(
                int playerId,
                int targetId
        ) implements FriendOrFoe {}

        enum Status {
            FRIEND,
            FOE
        }
    }

    record Avatars(int playerId) implements SocialRequest {}

    record SelectAvatar(
            int playerId,
            String avatarUrl
    ) implements SocialRequest {}

    record RemoveAvatar(int playerId) implements SocialRequest {}

}
