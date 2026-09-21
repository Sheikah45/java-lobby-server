package com.faforever.server.message;

import com.faforever.server.connection.TestLobbyConnection;
import com.faforever.server.endpoint.connection.UserAgent;
import com.faforever.server.exception.ClientException;
import com.faforever.server.message.external.AdminMessage;
import com.faforever.server.message.external.ConnectionMessage;
import com.faforever.server.message.external.GPGMessage;
import com.faforever.server.message.external.GameMessage;
import com.faforever.server.message.external.MatchmakerMessage;
import com.faforever.server.message.external.SocialMessage;
import com.faforever.server.message.external.dto.GameAccess;
import com.faforever.server.message.external.dto.GameVisibility;
import com.faforever.server.message.external.dto.MatchmakerState;
import com.faforever.server.message.internal.InboundLobbyMessage;
import com.faforever.server.player.PlayerService;
import com.faforever.server.policy.PolicyClient;
import com.faforever.server.policy.PolicyContents;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import io.quarkus.test.component.TestConfigProperty;
import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.auth.principal.ParseException;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@QuarkusComponentTest
class SessionHandlerTest {

    @Inject
    SessionHandler sessionHandler;

    @InjectMock
    private PlayerService playerService;
    @InjectMock
    private MessageBroker messageBroker;

    @InjectMock
    private PolicyClient policyClient;

    @InjectMock
    private JWTParser jwtParser;

    private final TestLobbyConnection connection = new TestLobbyConnection();

    @BeforeEach
    void setConnection() {
        sessionHandler.setConnection(connection);
    }

    @Test
    void testUnauthenticatedClientMessage() {
        assertThrows(ClientException.class,
                () -> sessionHandler.handleMessage(new SocialMessage.SocialAddRequest(null, null)));
        assertThrows(ClientException.class,
                () -> sessionHandler.handleMessage(new SocialMessage.SocialRemoveRequest(null, null)));
        assertThrows(ClientException.class,
                () -> sessionHandler.handleMessage(new SocialMessage.SelectAvatarRequest("")));
        assertThrows(ClientException.class,
                () -> sessionHandler.handleMessage(new SocialMessage.RemoveAvatarRequest()));
        assertThrows(ClientException.class,
                () -> sessionHandler.handleMessage(new SocialMessage.ListAvatarsRequest()));
        assertThrows(ClientException.class,
                () -> sessionHandler.handleMessage(new AdminMessage.BroadcastRequest("")));
        assertThrows(ClientException.class,
                () -> sessionHandler.handleMessage(new AdminMessage.KickPlayerRequest(0)));
        assertThrows(ClientException.class,
                () -> sessionHandler.handleMessage(new AdminMessage.ClosePlayerGameRequest(0)));
        assertThrows(ClientException.class, () -> sessionHandler.handleMessage(
                new MatchmakerMessage.GameMatchmakingRequest("", MatchmakerState.START)));
        assertThrows(ClientException.class,
                () -> sessionHandler.handleMessage(new MatchmakerMessage.InviteToPartyRequest(0)));
        assertThrows(ClientException.class,
                () -> sessionHandler.handleMessage(new MatchmakerMessage.AcceptInviteToPartyRequest(0)));
        assertThrows(ClientException.class,
                () -> sessionHandler.handleMessage(new MatchmakerMessage.IsReadyResponse("")));
        assertThrows(ClientException.class,
                () -> sessionHandler.handleMessage(new MatchmakerMessage.KickPlayerFromPartyRequest(0)));
        assertThrows(ClientException.class,
                () -> sessionHandler.handleMessage(new MatchmakerMessage.LeavePartyRequest()));
        assertThrows(ClientException.class,
                () -> sessionHandler.handleMessage(new MatchmakerMessage.MatchmakerInfoRequest()));
        assertThrows(ClientException.class,
                () -> sessionHandler.handleMessage(new MatchmakerMessage.SelectPartyFactionsRequest(Set.of())));
        assertThrows(ClientException.class,
                () -> sessionHandler.handleMessage(new MatchmakerMessage.SetPlayerVetoesRequest(List.of())));
        assertThrows(ClientException.class,
                () -> sessionHandler.handleMessage(new MatchmakerMessage.UnreadyPartyRequest()));
        assertThrows(ClientException.class, () -> sessionHandler.handleMessage(
                new GameMessage.HostGameRequest(null, "", null, GameAccess.PUBLIC, "", GameVisibility.PUBLIC, null,
                        null, false)));
        assertThrows(ClientException.class,
                () -> sessionHandler.handleMessage(new GameMessage.JoinGameRequest(0, null)));
        assertThrows(ClientException.class,
                () -> sessionHandler.handleMessage(new GameMessage.RestoreGameSessionRequest(0)));
        assertThrows(ClientException.class,
                () -> sessionHandler.handleMessage(new GPGMessage.Bottleneck(List.of())));

        verifyNoInteractions(playerService, messageBroker);
    }

    @Nested
    class Connection {
        @Test
        void testPing() {
            sessionHandler.handleMessage(new ConnectionMessage.Ping());
            assertThat(connection.getSentMessages(), contains(new ConnectionMessage.Pong()));
        }

        @Test
        void testPong() {
            sessionHandler.handleMessage(new ConnectionMessage.Pong());
            assertThat(connection.getSentMessages(), hasSize(0));
        }

        @Test
        void testSessionRequest() {
            UserAgent userAgent = new UserAgent("userAgent", "1.0");
            sessionHandler.handleMessage(
                    new ConnectionMessage.SessionRequest(userAgent.agent(), userAgent.version()));
            assertThat(connection.getSentMessages(),
                    contains(new ConnectionMessage.SessionResponse(sessionHandler.sessionId())));
            assertThat(sessionHandler.userAgent(), equalTo(userAgent));
        }

        @Test
        @TestConfigProperty(key = "faf.use-policy-server", value = "true")
        void testAuthenticateRequest() throws ParseException {
            int playerId = 1;
            JsonWebToken jsonWebToken = mock();
            when(jsonWebToken.getSubject()).thenReturn(Integer.toString(playerId));
            when(jwtParser.parse("")).thenReturn(jsonWebToken);

            sessionHandler.handleMessage(
                    new ConnectionMessage.AuthenticateRequest("", ""));

            assertDoesNotThrow(() -> sessionHandler.playerId());
            verify(playerService).registerSessionForPlayer(sessionHandler.sessionId(), playerId);
            verify(policyClient).checkPolicy(new PolicyContents(sessionHandler.sessionId(), playerId, ""));
        }

        @Test
        void testAuthenticateRequestNoPolicyServer() throws ParseException {
            int playerId = 1;
            JsonWebToken jsonWebToken = mock();
            when(jsonWebToken.getSubject()).thenReturn(Integer.toString(playerId));
            when(jwtParser.parse("")).thenReturn(jsonWebToken);

            sessionHandler.handleMessage(
                    new ConnectionMessage.AuthenticateRequest("", ""));

            assertDoesNotThrow(() -> sessionHandler.playerId());
            verify(playerService).registerSessionForPlayer(sessionHandler.sessionId(), playerId);
            verifyNoInteractions(policyClient);
        }
    }

    @Nested
    class Authenticated {

        private static final int PLAYER_ID = 1;

        @BeforeEach
        void setup() throws ParseException {
            JsonWebToken jsonWebToken = mock();
            when(jsonWebToken.getSubject()).thenReturn(Integer.toString(PLAYER_ID));
            when(jwtParser.parse("")).thenReturn(jsonWebToken);

            sessionHandler.handleMessage(
                    new ConnectionMessage.AuthenticateRequest("", ""));
        }

        @Test
        void testSocialAddRequest() {
            SocialMessage.SocialAddRequest message = new SocialMessage.SocialAddRequest(2, 3);
            sessionHandler.handleMessage(message);
            verify(messageBroker).handleInboundMessage(new InboundLobbyMessage<>(sessionHandler.sessionId(), PLAYER_ID, message));
        }

        @Test
        void testSocialRemoveRequest() {
            SocialMessage.SocialRemoveRequest message = new SocialMessage.SocialRemoveRequest(2, 3);
            sessionHandler.handleMessage(message);
            verify(messageBroker).handleInboundMessage(new InboundLobbyMessage<>(sessionHandler.sessionId(), PLAYER_ID, message));
        }

        @Test
        void testAvatarSelectionRequest() {
            SocialMessage.SelectAvatarRequest message = new SocialMessage.SelectAvatarRequest("temp");
            sessionHandler.handleMessage(message);
            verify(messageBroker).handleInboundMessage(new InboundLobbyMessage<>(sessionHandler.sessionId(), PLAYER_ID, message));
        }


        @Test
        void testAvatarRemoveRequest() {
            SocialMessage.RemoveAvatarRequest message = new SocialMessage.RemoveAvatarRequest();
            sessionHandler.handleMessage(message);
            verify(messageBroker).handleInboundMessage(new InboundLobbyMessage<>(sessionHandler.sessionId(), PLAYER_ID, message));
        }

        @Test
        void testAvatarListRequest() {
            SocialMessage.ListAvatarsRequest message = new SocialMessage.ListAvatarsRequest();
            sessionHandler.handleMessage(message);

            verify(messageBroker).handleInboundMessage(new InboundLobbyMessage<>(sessionHandler.sessionId(), PLAYER_ID, message));
        }

        @Test
        void testBroadcastRequest() {
            AdminMessage.BroadcastRequest message = new AdminMessage.BroadcastRequest("test");
            sessionHandler.handleMessage(message);

            verify(messageBroker).handleInboundMessage(new InboundLobbyMessage<>(sessionHandler.sessionId(), PLAYER_ID, message));
        }

        @Test
        void testKickRequest() {
            AdminMessage.KickPlayerRequest message = new AdminMessage.KickPlayerRequest(2);
            sessionHandler.handleMessage(message);

            verify(messageBroker).handleInboundMessage(new InboundLobbyMessage<>(sessionHandler.sessionId(), PLAYER_ID, message));
        }

        @Test
        void testCloseGameRequest() {
            AdminMessage.ClosePlayerGameRequest message = new AdminMessage.ClosePlayerGameRequest(2);
            sessionHandler.handleMessage(message);

            verify(messageBroker).handleInboundMessage(new InboundLobbyMessage<>(sessionHandler.sessionId(), PLAYER_ID, message));
        }
    }
}
