package com.faforever.server.message;

import com.faforever.server.message.dto.PlayerInfo;
import org.jspecify.annotations.Nullable;

public sealed interface ConnectionMessage {

    sealed interface Server extends ConnectionMessage, LobbyMessage.Server {}

    sealed interface Client extends ConnectionMessage, LobbyMessage.Client {}

    record Ping() implements Server, Client, LobbyMessage.Broadcast {}

    record Pong() implements Server, Client {}

    record SessionRequest(
            @Nullable String userAgent,
            @Nullable String version
    ) implements Client {}

    record AuthenticateRequest(
            String token,
            String uniqueId
    ) implements Client {}

    record SessionResponse(
            long session
    ) implements Server {}

    record LoginSuccessResponse(
            PlayerInfo me
    ) implements Server {}

    record LoginFailureResponse(
            @Nullable String text
    ) implements Server {}

    record NoticeInfo(
            @Nullable String style,
            @Nullable String text
    ) implements Server {}

}
