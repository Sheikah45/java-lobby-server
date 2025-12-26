package com.faforever.server.message;

import com.fasterxml.jackson.annotation.JsonProperty;

public sealed interface MatchmakerMessage {

    sealed interface Client extends MatchmakerMessage, LobbyMessage.Client {}

//    sealed interface Server extends MatchmakerMessage, LobbyMessage.Server {}

    enum MatchmakerState {
        @JsonProperty("start") START,
        @JsonProperty("stop") STOP
    }

    record GameMatchmakingRequest(
            String queueName,
            MatchmakerState state
    ) implements Client {}

    record InviteToPartyRequest(
            int recipientId
    ) implements Client {}
}
