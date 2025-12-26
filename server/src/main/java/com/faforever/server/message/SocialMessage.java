package com.faforever.server.message;

import com.faforever.server.message.dto.AvatarInfo;
import com.faforever.server.message.dto.PlayerInfo;
import com.fasterxml.jackson.annotation.*;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Map;

public sealed interface SocialMessage  {

    sealed interface Server extends SocialMessage, LobbyMessage.Server {}

    sealed interface Client extends SocialMessage, LobbyMessage.Client {}

    record PlayerInfoList(
            Collection<PlayerInfo> players
    ) implements Server, LobbyMessage.Broadcast {}

    record AvatarInfoList(
            Collection<AvatarInfo> avatars
    ) implements Server {}

    record SocialInfo(
            Collection<String> channels,
            @JsonProperty("friends") Collection<Integer> friendIds,
            @JsonProperty("foes") Collection<Integer> foeIds
    ) {}

    record SocialAddRequest(
            @JsonProperty("friend")
            @Nullable Integer friendId,
            @JsonProperty("foe")
            @Nullable Integer foeId
    ) implements Client {}

    record SocialRemoveRequest(
            @JsonProperty("friend")
            @Nullable Integer friendId,
            @JsonProperty("foe")
            @Nullable Integer foeId
    ) implements Client {}

    sealed interface AvatarRequest extends Client {

        enum Action {
            @JsonProperty("select") SELECT, @JsonProperty("list_avatar") LIST_AVATAR
        }

        @JsonCreator
        static AvatarRequest of(@JsonProperty("action") Action action, @JsonAnySetter Map<String, String> properties) {
            return switch (action) {
                case LIST_AVATAR -> new ListAvatarsRequest();
                case SELECT -> new SelectAvatarRequest(properties.get("avatar"));
            };
        }

    }

    record ListAvatarsRequest() implements AvatarRequest {}

    record SelectAvatarRequest(
            @Nullable String avatar
    ) implements AvatarRequest {}
}
