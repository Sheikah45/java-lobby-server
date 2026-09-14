package com.faforever.server.player;

public sealed interface SocialRequest {

    long sessionId();

    sealed interface FriendOrFoe extends SocialRequest {


        int targetId();

        record Add(
                long sessionId,
                int targetId,
                Status status
        ) implements FriendOrFoe {}

        record Remove(
                long sessionId,
                int targetId
        ) implements FriendOrFoe {}

        enum Status {
            FRIEND,
            FOE
        }
    }

    record Avatars(long sessionId) implements SocialRequest {}

    record SelectAvatar(
            long sessionId,
            String avatarUrl
    ) implements SocialRequest {}

    record RemoveAvatar(long sessionId) implements SocialRequest {}

}
