package com.faforever.server.social;

import org.jspecify.annotations.Nullable;

public sealed interface SocialRequest {

    sealed interface FriendOrFoe extends SocialRequest {

        int playerId();
        int targetId();

        record AddFriend(int playerId, int targetId) implements FriendOrFoe {}
        record RemoveFriend(int playerId, int targetId) implements FriendOrFoe {}
        record AddFoe(int playerId, int targetId) implements FriendOrFoe {}
        record RemoveFoe(int playerId, int targetId) implements FriendOrFoe {}
    }

    record Avatars(int playerId) implements SocialRequest {}

    record SelectAvatar(int playerId, @Nullable String avatarUrl) implements SocialRequest {}

}
