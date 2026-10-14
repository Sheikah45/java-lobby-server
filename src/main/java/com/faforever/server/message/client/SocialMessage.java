package com.faforever.server.message.client;

import com.faforever.server.message.client.dto.AvatarInfo;
import com.faforever.server.message.client.dto.PlayerInfo;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Map;

public sealed interface SocialMessage  {

    sealed interface Server extends SocialMessage, LobbyMessage.Server {}

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "command")
    @JsonSubTypes(
            {
                    @JsonSubTypes.Type(value = SocialMessage.SocialAddRequest.class, name = "social_add"),
                    @JsonSubTypes.Type(value = SocialMessage.SocialRemoveRequest.class, name = "social_remove"),
                    @JsonSubTypes.Type(value = SocialMessage.AvatarRequest.class, name = "avatar"),
            }
    )
    sealed interface Client extends SocialMessage, LobbyMessage.Client {}

    record PlayerInfoList(
            Collection<PlayerInfo> players
    ) implements Server, LobbyMessage.Broadcast {}

    record AvatarInfoList(
            @JsonProperty("avatarlist") Collection<AvatarInfo> avatars
    ) implements Server {}

    record SocialInfo(
            Collection<String> channels,
            @JsonProperty("friends") Collection<Integer> friendIds,
            @JsonProperty("foes") Collection<Integer> foeIds
    ) implements Server {

        @JsonProperty("autojoin")
        Collection<String> autojoin() {
            return channels;
        }
    }

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
                case SELECT -> {
                    String avatarUrl = properties.get("avatar");
                    yield avatarUrl == null ? new RemoveAvatarRequest() : new SelectAvatarRequest(avatarUrl);
                }
            };
        }

    }

    record ListAvatarsRequest() implements AvatarRequest {}

    record SelectAvatarRequest(
            @JsonProperty("avatar") String avatarUrl
    ) implements AvatarRequest {}

    record RemoveAvatarRequest() implements AvatarRequest {}
}
