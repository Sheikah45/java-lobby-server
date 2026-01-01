package com.faforever.server.message;

import com.faforever.server.message.dto.MatchmakerQueue;
import com.faforever.server.message.dto.MatchmakerState;
import com.faforever.server.message.dto.PartyMember;
import com.faforever.server.message.dto.VetoData;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Set;

public sealed interface MatchmakerMessage {

    sealed interface Client extends MatchmakerMessage, LobbyMessage.Client {}

    sealed interface Server extends MatchmakerMessage, LobbyMessage.Server {}

    record GameMatchmakingRequest(
            String queueName,
            MatchmakerState state
    ) implements Client {}

    record InviteToPartyRequest(
            int recipientId
    ) implements Client {}

    record IsReadyResponse(
            String requestId
    ) implements Client {}

    record SetPlayerVetoesRequest(
            List<VetoData> vetoes
    ) implements Client {}

    record AcceptInviteToPartyRequest(
            int senderId
    ) implements Client {}

    record KickPlayerFromPartyRequest(
            int kickedPlayerId
    ) implements Client {}

    record UnreadyPartyRequest() implements Client {}

    record LeavePartyRequest() implements Client {}

    record MatchmakerInfoRequest() implements Client {}

    record SelectPartyFactionsRequest(
            Set<Faction> factions
    ) implements Client {}

    record MatchmakerInfo(
            Set<MatchmakerQueue> queues
    ) implements Server, LobbyMessage.Broadcast {}

    record MatchmakerMatchFoundResponse(
            String queueName
    ) implements Server {}

    record MatchmakerMatchCancelledResponse() implements Server {}

    record SearchInfo(
            String queueName,
            MatchmakerState state
    ) implements Server {}

    record IsReadyRequest(
            String gameName,
            String featuredMod,
            int responseTimeSeconds,
            String requestId
    ) implements Server {}

    record PartyInvite(
           @JsonProperty("sender") int senderId
    ) implements Server {}

    record PartyKick() implements Server {}

    record PartyInfo(
            @JsonProperty("owner") int ownerId,
            List<PartyMember> members
    ) implements Server {}

    record VetoesChangedInfo(
            boolean forced,
            List<VetoData> vetoes
    ) implements Server {}
}
