package com.faforever.server.connection;

import com.faforever.server.admin.AdminRequest;
import com.faforever.server.admin.AdminService;
import com.faforever.server.broadcast.BroadcastService;
import com.faforever.server.exception.ClientException;
import com.faforever.server.game.GPGService;
import com.faforever.server.game.GameService;
import com.faforever.server.mapstruct.OptionalMapperImpl;
import com.faforever.server.matchmaker.MatchmakerService;
import com.faforever.server.message.AdminMessage;
import com.faforever.server.message.ConnectionMessage;
import com.faforever.server.message.GPGMessage;
import com.faforever.server.message.GameMessage;
import com.faforever.server.message.MatchmakerMessage;
import com.faforever.server.message.SocialMessage;
import com.faforever.server.message.dto.AvatarInfo;
import com.faforever.server.message.dto.DtoMapperImpl;
import com.faforever.server.message.dto.GameAccess;
import com.faforever.server.message.dto.GameVisibility;
import com.faforever.server.message.dto.MatchmakerState;
import com.faforever.server.player.Avatar;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@QuarkusComponentTest(value = {DtoMapperImpl.class, OptionalMapperImpl.class})
class SessionControllerTest {

    private static final int PLAYER_ID = 1;

    @Inject
    SessionController sessionController;

    @InjectMock
    private PlayerService playerService;
    @InjectMock
    private BroadcastService broadcastService;
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
        sessionController.setConnection(connection);
    }

    @Test
    void testUnauthenticatedClientMessage() {
        assertThrows(ClientException.class,
                () -> sessionController.handleMessage(new SocialMessage.SocialAddRequest(null, null)));
        assertThrows(ClientException.class,
                () -> sessionController.handleMessage(new SocialMessage.SocialRemoveRequest(null, null)));
        assertThrows(ClientException.class,
                () -> sessionController.handleMessage(new SocialMessage.SelectAvatarRequest("")));
        assertThrows(ClientException.class,
                () -> sessionController.handleMessage(new SocialMessage.RemoveAvatarRequest()));
        assertThrows(ClientException.class,
                () -> sessionController.handleMessage(new SocialMessage.ListAvatarsRequest()));
        assertThrows(ClientException.class,
                () -> sessionController.handleMessage(new AdminMessage.BroadcastRequest("")));
        assertThrows(ClientException.class,
                () -> sessionController.handleMessage(new AdminMessage.KickPlayerRequest(0)));
        assertThrows(ClientException.class,
                () -> sessionController.handleMessage(new AdminMessage.ClosePlayerGameRequest(0)));
        assertThrows(ClientException.class, () -> sessionController.handleMessage(
                new MatchmakerMessage.GameMatchmakingRequest("", MatchmakerState.START)));
        assertThrows(ClientException.class,
                () -> sessionController.handleMessage(new MatchmakerMessage.InviteToPartyRequest(0)));
        assertThrows(ClientException.class,
                () -> sessionController.handleMessage(new MatchmakerMessage.AcceptInviteToPartyRequest(0)));
        assertThrows(ClientException.class,
                () -> sessionController.handleMessage(new MatchmakerMessage.IsReadyResponse("")));
        assertThrows(ClientException.class,
                () -> sessionController.handleMessage(new MatchmakerMessage.KickPlayerFromPartyRequest(0)));
        assertThrows(ClientException.class,
                () -> sessionController.handleMessage(new MatchmakerMessage.LeavePartyRequest()));
        assertThrows(ClientException.class,
                () -> sessionController.handleMessage(new MatchmakerMessage.MatchmakerInfoRequest()));
        assertThrows(ClientException.class,
                () -> sessionController.handleMessage(new MatchmakerMessage.SelectPartyFactionsRequest(Set.of())));
        assertThrows(ClientException.class,
                () -> sessionController.handleMessage(new MatchmakerMessage.SetPlayerVetoesRequest(List.of())));
        assertThrows(ClientException.class,
                () -> sessionController.handleMessage(new MatchmakerMessage.UnreadyPartyRequest()));
        assertThrows(ClientException.class, () -> sessionController.handleMessage(
                new GameMessage.HostGameRequest(null, "", null, GameAccess.PUBLIC, "", GameVisibility.PUBLIC, null,
                        null, false)));
        assertThrows(ClientException.class,
                () -> sessionController.handleMessage(new GameMessage.JoinGameRequest(0, null)));
        assertThrows(ClientException.class,
                () -> sessionController.handleMessage(new GameMessage.RestoreGameSessionRequest(0)));
        assertThrows(ClientException.class,
                () -> sessionController.handleMessage(new GPGMessage.Bottleneck(List.of())));

        verifyNoInteractions(playerService, broadcastService, adminService, gameService, gpgService, matchmakerService);
    }

    @Nested
    class Connection {
        @Test
        void testPing() {
            sessionController.handleMessage(new ConnectionMessage.Ping());
            assertThat(connection.getSentMessages(), contains(new ConnectionMessage.Pong()));
        }

        @Test
        void testPong() {
            sessionController.handleMessage(new ConnectionMessage.Pong());
            assertThat(connection.getSentMessages(), hasSize(0));
        }

        @Test
        void testSessionRequest() {
            UserAgent userAgent = new UserAgent("userAgent", "1.0");
            sessionController.handleMessage(
                    new ConnectionMessage.SessionRequest(userAgent.agent(), userAgent.version()));
            assertThat(connection.getSentMessages(),
                    contains(new ConnectionMessage.SessionResponse(sessionController.sessionId())));
            assertThat(sessionController.userAgent().orElseThrow(), equalTo(userAgent));
        }

        @Test
        void testAuthenticateRequest() throws ParseException {
            JsonWebToken jsonWebToken = mock();
            when(jsonWebToken.getSubject()).thenReturn(Integer.toString(PLAYER_ID));
            when(jwtParser.parse("")).thenReturn(jsonWebToken);

            sessionController.handleMessage(
                    new ConnectionMessage.AuthenticateRequest("", ""));

            assertThat(sessionController.playerId().orElseThrow(), equalTo(PLAYER_ID));
            verify(playerService).registerSession(sessionController);
            verify(broadcastService).registerSession(sessionController);
        }
    }

    @Nested
    class Authenticated {

        @BeforeEach
        void setup() {
            SessionControllerTestUtils.setSessionPlayer(sessionController, PLAYER_ID);
        }

        @Test
        void testFriendFoeRequest() {
            int friendId = 2;
            sessionController.handleMessage(new SocialMessage.SocialAddRequest(friendId, null));
            verify(playerService).changeSocialRelationship(
                    new SocialRequest.FriendOrFoe.Add(PLAYER_ID, friendId, SocialRequest.FriendOrFoe.Status.FRIEND));

            int foeId = 3;
            sessionController.handleMessage(new SocialMessage.SocialAddRequest(null, foeId));
            verify(playerService).changeSocialRelationship(
                    new SocialRequest.FriendOrFoe.Add(PLAYER_ID, foeId, SocialRequest.FriendOrFoe.Status.FOE));

            sessionController.handleMessage(new SocialMessage.SocialRemoveRequest(friendId, null));
            verify(playerService).changeSocialRelationship(new SocialRequest.FriendOrFoe.Remove(PLAYER_ID, friendId));

            sessionController.handleMessage(new SocialMessage.SocialRemoveRequest(null, foeId));
            verify(playerService).changeSocialRelationship(new SocialRequest.FriendOrFoe.Remove(PLAYER_ID, foeId));
        }

        @Test
        void testAvatarSelectionRequest() {
            sessionController.handleMessage(new SocialMessage.SelectAvatarRequest("temp"));
            verify(playerService).selectAvatar(new SocialRequest.SelectAvatar(PLAYER_ID, "temp"));

            sessionController.handleMessage(new SocialMessage.RemoveAvatarRequest());
            verify(playerService).removeAvatar(new SocialRequest.RemoveAvatar(PLAYER_ID));
        }

        @Test
        void testAvatarListRequest() {
            when(playerService.getAvatars(new SocialRequest.Avatars(PLAYER_ID))).thenReturn(
                    Set.of(new Avatar("temp", "temporary")));
            sessionController.handleMessage(new SocialMessage.ListAvatarsRequest());

            assertThat(connection.getSentMessages(),
                    contains(new SocialMessage.AvatarInfoList(List.of(new AvatarInfo("temp", "temporary")))));

        }

        @Test
        void testBroadcastRequest() {
            sessionController.handleMessage(new AdminMessage.BroadcastRequest("test"));

            verify(adminService).handleRequest(new AdminRequest.Broadcast(PLAYER_ID, "test"));
        }

        @Test
        void testKickRequest() {
            sessionController.handleMessage(new AdminMessage.KickPlayerRequest(2));

            verify(adminService).handleRequest(new AdminRequest.KickPlayer(PLAYER_ID, 2));
        }

        @Test
        void testCloseGameRequest() {
            sessionController.handleMessage(new AdminMessage.ClosePlayerGameRequest(2));

            verify(adminService).handleRequest(new AdminRequest.ClosePlayerGame(PLAYER_ID, 2));
        }
    }
}
