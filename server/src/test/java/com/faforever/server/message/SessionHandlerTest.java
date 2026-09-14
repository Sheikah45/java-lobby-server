package com.faforever.server.message;

import com.faforever.server.admin.AdminRequest;
import com.faforever.server.admin.AdminService;
import com.faforever.server.connection.TestLobbyConnection;
import com.faforever.server.connection.UserAgent;
import com.faforever.server.exception.ClientException;
import com.faforever.server.game.GPGService;
import com.faforever.server.game.GameService;
import com.faforever.server.mapstruct.OptionalMapperImpl;
import com.faforever.server.matchmaker.MatchmakerService;
import com.faforever.server.message.external.AdminMessage;
import com.faforever.server.message.external.ConnectionMessage;
import com.faforever.server.message.external.GPGMessage;
import com.faforever.server.message.external.GameMessage;
import com.faforever.server.message.external.MatchmakerMessage;
import com.faforever.server.message.external.SocialMessage;
import com.faforever.server.message.external.dto.DtoMapperImpl;
import com.faforever.server.message.external.dto.GameAccess;
import com.faforever.server.message.external.dto.GameVisibility;
import com.faforever.server.message.external.dto.MatchmakerState;
import com.faforever.server.player.PlayerService;
import com.faforever.server.player.SocialRequest;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
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

@QuarkusComponentTest(value = {DtoMapperImpl.class, OptionalMapperImpl.class})
class SessionHandlerTest {

    @Inject
    SessionHandler sessionHandler;

    @InjectMock
    private PlayerService playerService;
    @InjectMock
    private AdminService adminService;
    @InjectMock
    private MatchmakerService matchmakerService;
    @InjectMock
    private GameService gameService;
    @InjectMock
    private GPGService gpgService;

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

        verifyNoInteractions(playerService, adminService, gameService, gpgService, matchmakerService);
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
        void testAuthenticateRequest() throws ParseException {
            int playerId = 1;
            JsonWebToken jsonWebToken = mock();
            when(jsonWebToken.getSubject()).thenReturn(Integer.toString(playerId));
            when(jwtParser.parse("")).thenReturn(jsonWebToken);

            sessionHandler.handleMessage(
                    new ConnectionMessage.AuthenticateRequest("", ""));

            assertDoesNotThrow(() -> sessionHandler.playerId());
            verify(playerService).registerSessionForPlayer(sessionHandler.sessionId(), playerId);
        }
    }

    @Nested
    class Authenticated {

        @BeforeEach
        void setup() throws ParseException {
            JsonWebToken jsonWebToken = mock();
            when(jsonWebToken.getSubject()).thenReturn("1");
            when(jwtParser.parse("")).thenReturn(jsonWebToken);

            sessionHandler.handleMessage(
                    new ConnectionMessage.AuthenticateRequest("", ""));
        }

        @Test
        void testFriendFoeRequest() {
            int friendId = 2;
            sessionHandler.handleMessage(new SocialMessage.SocialAddRequest(friendId, null));
            verify(playerService).changeSocialRelationship(
                    new SocialRequest.FriendOrFoe.Add(sessionHandler.sessionId(), friendId, SocialRequest.FriendOrFoe.Status.FRIEND));

            int foeId = 3;
            sessionHandler.handleMessage(new SocialMessage.SocialAddRequest(null, foeId));
            verify(playerService).changeSocialRelationship(
                    new SocialRequest.FriendOrFoe.Add(sessionHandler.sessionId(), foeId, SocialRequest.FriendOrFoe.Status.FOE));

            sessionHandler.handleMessage(new SocialMessage.SocialRemoveRequest(friendId, null));
            verify(playerService).changeSocialRelationship(new SocialRequest.FriendOrFoe.Remove(sessionHandler.sessionId(), friendId));

            sessionHandler.handleMessage(new SocialMessage.SocialRemoveRequest(null, foeId));
            verify(playerService).changeSocialRelationship(new SocialRequest.FriendOrFoe.Remove(sessionHandler.sessionId(), foeId));
        }

        @Test
        void testAvatarSelectionRequest() {
            sessionHandler.handleMessage(new SocialMessage.SelectAvatarRequest("temp"));
            verify(playerService).selectAvatar(new SocialRequest.SelectAvatar(sessionHandler.sessionId(), "temp"));

            sessionHandler.handleMessage(new SocialMessage.RemoveAvatarRequest());
            verify(playerService).removeAvatar(new SocialRequest.RemoveAvatar(sessionHandler.sessionId()));
        }

        @Test
        void testAvatarListRequest() {
            sessionHandler.handleMessage(new SocialMessage.ListAvatarsRequest());

            verify(playerService).sendAvatars(new SocialRequest.Avatars(sessionHandler.sessionId()));
        }

        @Test
        void testBroadcastRequest() {
            sessionHandler.handleMessage(new AdminMessage.BroadcastRequest("test"));

            verify(adminService).handleRequest(new AdminRequest.Broadcast(sessionHandler.sessionId(), "test"));
        }

        @Test
        void testKickRequest() {
            sessionHandler.handleMessage(new AdminMessage.KickPlayerRequest(2));

            verify(adminService).handleRequest(new AdminRequest.KickPlayer(sessionHandler.sessionId(), 2));
        }

        @Test
        void testCloseGameRequest() {
            sessionHandler.handleMessage(new AdminMessage.ClosePlayerGameRequest(2));

            verify(adminService).handleRequest(new AdminRequest.ClosePlayerGame(sessionHandler.sessionId(), 2));
        }
    }
}
