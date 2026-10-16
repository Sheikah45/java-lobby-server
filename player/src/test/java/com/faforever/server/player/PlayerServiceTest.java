package com.faforever.server.player;

import com.faforever.server.domain.FriendOrFoeEntity;
import com.faforever.server.message.client.AdminMessage;
import com.faforever.server.message.client.ConnectionMessage;
import com.faforever.server.message.client.LobbyMessage;
import com.faforever.server.message.client.SocialMessage;
import com.faforever.server.message.client.dto.AvatarInfo;
import com.faforever.server.message.client.dto.LeaderboardStats;
import com.faforever.server.message.client.dto.PlayerInfo;
import com.faforever.server.message.service.InboundLobbyMessage;
import com.faforever.server.message.service.OutboundLobbyMessage;
import com.faforever.server.message.service.OutboundTarget;
import com.faforever.server.rating.Leaderboard;
import com.faforever.server.rating.LeaderboardRating;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.memory.TestingConnector;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.isA;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@QuarkusTest
class PlayerServiceTest {

    private static final int PLAYER_ID = 1;
    private static final long SESSION_ID = 0;
    private static final PlayerInfo PLAYER_INFO = new PlayerInfo(PLAYER_ID, "test", "TEST",
            new AvatarInfo("temp", "temporary"), "",
            Map.of("leaderboard", new LeaderboardStats(1, new LeaderboardStats.Rating(1000, 100))), null);
    private static final Avatar AVATAR = new Avatar("temp", "temporary");
    private static final String LOBBY_OUTBOUND_CHANNEL = "lobby-outbound";

    @Inject
    Instance<PlayerService> playerServiceInstance;

    private PlayerService playerService;

    @Inject
    @Connector("smallrye-testing")
    TestingConnector testingConnector;

    @InjectMock
    private PlayerRepository playerRepository;
    @InjectMock
    private FriendOrFoeRepository friendOrFoeRepository;
    @InjectMock
    private AssignedAvatarRepository assignedAvatarRepository;
    @InjectMock
    private UserGroupAssignmentRepository userGroupAssignmentRepository;

    private final Player player = new Player(PLAYER_ID, "test");

    @BeforeEach
    void setup() {
        playerService = playerServiceInstance.get();

        player.setClan("TEST");
        player.setAvatar(AVATAR);
        player.updateLeaderboardRatings(List.of(new LeaderboardRating(new Leaderboard("leaderboard"), 1, 1000, 100)));

        when(playerRepository.loadPlayer(PLAYER_ID)).thenReturn(player);
    }

    @AfterEach
    void teardown() {
        playerServiceInstance.destroy(playerService);
        testingConnector.<OutboundLobbyMessage<?>>outgoing(LOBBY_OUTBOUND_CHANNEL).clear();
    }

    @Nested
    class Unregistered {

        @Test
        void testRegisterSession() {
            playerService.registerSessionForPlayer(SESSION_ID, PLAYER_ID);

            assertThat(playerService.getDirtyPlayers(), contains(player));

            List<? extends Message<OutboundLobbyMessage<?>>> sentMessages = testingConnector.<OutboundLobbyMessage<?>>outgoing(
                    LOBBY_OUTBOUND_CHANNEL).sent();
            assertEquals(3, sentMessages.size());

            OutboundLobbyMessage<?> firstMessage = sentMessages.getFirst().getPayload();
            OutboundTarget firstTarget = firstMessage.target();
            assertThat(firstTarget, isA(OutboundTarget.Session.class));

            OutboundTarget.Session firstSessionTarget = (OutboundTarget.Session) firstTarget;
            assertThat(firstSessionTarget.sessionId(), equalTo(SESSION_ID));

            assertThat(firstMessage.message(), isA(ConnectionMessage.LoginSuccessResponse.class));

            ConnectionMessage.LoginSuccessResponse loginSuccessResponse = (ConnectionMessage.LoginSuccessResponse) firstMessage.message();
            assertThat(loginSuccessResponse.currentTime(), lessThanOrEqualTo(OffsetDateTime.now()));
            assertThat(loginSuccessResponse.me(), equalTo(PLAYER_INFO));

            OutboundLobbyMessage<?> secondMessage = sentMessages.get(1).getPayload();
            OutboundTarget secondTarget = secondMessage.target();
            assertThat(secondTarget, isA(OutboundTarget.Session.class));

            OutboundTarget.Session secondSessionTarget = (OutboundTarget.Session) secondTarget;

            assertThat(secondSessionTarget.sessionId(), equalTo(SESSION_ID));
            assertThat(secondMessage.message(),
                    equalTo(new SocialMessage.SocialInfo(Set.of("#TEST_clan"), Set.of(), Set.of())));

            OutboundLobbyMessage<?> thirdMessage = sentMessages.getLast().getPayload();
            OutboundTarget thirdTarget = thirdMessage.target();
            assertThat(thirdTarget, isA(OutboundTarget.Session.class));

            OutboundTarget.Session thirdSessionTarget = (OutboundTarget.Session) thirdTarget;

            assertThat(thirdSessionTarget.sessionId(), equalTo(SESSION_ID));
            assertThat(thirdMessage.message(), isA(SocialMessage.PlayerInfoList.class));
        }

        @Test
        void testRegisterSessionAsModerator() {
            when(userGroupAssignmentRepository.isPlayerModerator(PLAYER_ID)).thenReturn(true);

            playerService.registerSessionForPlayer(SESSION_ID, PLAYER_ID);

            List<? extends Message<OutboundLobbyMessage<?>>> sentMessages = testingConnector.<OutboundLobbyMessage<?>>outgoing(
                    LOBBY_OUTBOUND_CHANNEL).sent();
            assertEquals(3, sentMessages.size());

            OutboundLobbyMessage<?> secondMessage = sentMessages.get(1).getPayload();
            OutboundTarget secondTarget = secondMessage.target();
            assertThat(secondTarget, isA(OutboundTarget.Session.class));

            OutboundTarget.Session secondSessionTarget = (OutboundTarget.Session) secondTarget;

            assertThat(secondSessionTarget.sessionId(), equalTo(SESSION_ID));
            assertThat(secondMessage.message(),
                    equalTo(new SocialMessage.SocialInfo(Set.of("#TEST_clan", "#moderators"), Set.of(), Set.of())));
        }

        @Test
        void testChangeSocialRelationshipOfflinePlayer() {
            playerService.handleSocialRequest(createInboundMessage(new SocialMessage.SocialAddRequest(2, 3)));
            playerService.handleSocialRequest(createInboundMessage(new SocialMessage.SocialRemoveRequest(2, 3)));

            assertThat(player.getFriendIds(), hasSize(0));
            assertThat(player.getFoeIds(), hasSize(0));
            verifyNoInteractions(friendOrFoeRepository);
        }

        @Test
        void testRemoveAvatarOfflinePlayer() {
            playerService.handleSocialRequest(createInboundMessage(new SocialMessage.RemoveAvatarRequest()));

            assertThat(player.getAvatar().orElseThrow(), equalTo(AVATAR));
            assertThat(playerService.getDirtyPlayers(), hasSize(0));
            verifyNoInteractions(assignedAvatarRepository);
        }

        @Test
        void testSelectAvatarOfflinePlayer() {
            playerService.handleSocialRequest(createInboundMessage(new SocialMessage.SelectAvatarRequest("test")));

            assertThat(player.getAvatar().orElseThrow(), equalTo(AVATAR));
            assertThat(playerService.getDirtyPlayers(), hasSize(0));
            verifyNoInteractions(assignedAvatarRepository);
        }

        @Test
        void testSendAvatarsOfflinePlayer() {
            playerService.handleSocialRequest(createInboundMessage(new SocialMessage.ListAvatarsRequest()));

            List<? extends Message<OutboundLobbyMessage<?>>> sentMessages = testingConnector.<OutboundLobbyMessage<?>>outgoing(
                    LOBBY_OUTBOUND_CHANNEL).sent();
            assertEquals(0, sentMessages.size());

            verifyNoInteractions(assignedAvatarRepository);
        }

        @Test
        void testMarkDirtyOfflinePlayer() {
            playerService.markDirty(player);

            assertThat(playerService.getDirtyPlayers(), hasSize(0));
        }

        @Test
        void testMarkDisconnectedOfflinePlayer() {
            playerService.markDisconnected(player);

            assertThat(playerService.getDisconnectedPlayers(), hasSize(0));
        }

        @Test
        void testNoActionWhenNoDirty() {
            assertThat(playerService.getDirtyPlayers(), hasSize(0));
            playerService.sendUpdateForDirtyPlayers();
            List<? extends Message<OutboundLobbyMessage<?>>> sentMessages = testingConnector.<OutboundLobbyMessage<?>>outgoing(
                    LOBBY_OUTBOUND_CHANNEL).sent();
            assertEquals(0, sentMessages.size());
        }

        @Test
        void testNoActionWhenNoDisconnected() {
            assertThat(playerService.getDisconnectedPlayers(), hasSize(0));
            playerService.removeDisconnectedPlayers();
            assertThat(playerService.getDisconnectedPlayers(), hasSize(0));
            assertThat(playerService.getDirtyPlayers(), hasSize(0));
        }

        @Test
        void testUnregisterSession() {
            playerService.unregisterSession(SESSION_ID);

            assertThat(playerService.getDisconnectedPlayers(), hasSize(0));
        }

        @Test
        void testBroadcastNoPlayer() {
            playerService.handleAdminRequest(createInboundMessage(new AdminMessage.BroadcastRequest("test")));

            List<? extends Message<OutboundLobbyMessage<?>>> sentMessages = testingConnector.<OutboundLobbyMessage<?>>outgoing(
                    LOBBY_OUTBOUND_CHANNEL).sent();
            assertEquals(0, sentMessages.size());
            verifyNoInteractions(userGroupAssignmentRepository);
        }

        @Test
        void testKickNoPlayer() {
            playerService.handleAdminRequest(createInboundMessage(new AdminMessage.KickPlayerRequest(2)));

            List<? extends Message<OutboundLobbyMessage<?>>> sentMessages = testingConnector.<OutboundLobbyMessage<?>>outgoing(
                    LOBBY_OUTBOUND_CHANNEL).sent();
            assertEquals(0, sentMessages.size());
            verifyNoInteractions(userGroupAssignmentRepository);
        }

        @Test
        void testCloseGameNoPlayer() {
            playerService.handleAdminRequest(createInboundMessage(new AdminMessage.ClosePlayerGameRequest(2)));

            List<? extends Message<OutboundLobbyMessage<?>>> sentMessages = testingConnector.<OutboundLobbyMessage<?>>outgoing(
                    LOBBY_OUTBOUND_CHANNEL).sent();
            assertEquals(0, sentMessages.size());
            verifyNoInteractions(userGroupAssignmentRepository);
        }
    }

    @Nested
    class Registered {

        @BeforeEach
        void setup() {
            playerService.registerSessionForPlayer(SESSION_ID, PLAYER_ID);
            playerService.getDirtyPlayers().clear();
            testingConnector.<OutboundLobbyMessage<?>>outgoing(LOBBY_OUTBOUND_CHANNEL).clear();
            Mockito.clearInvocations(userGroupAssignmentRepository);
        }

        @Test
        void testDoubleRegistrationThrowsWhenDifferentPlayers() {
            when(playerRepository.loadPlayer(2)).thenReturn(new Player(2, "test"));
            assertThrows(IllegalStateException.class, () -> playerService.registerSessionForPlayer(SESSION_ID, 2));
        }

        @Test
        void testDoubleRegistrationDoesNotThrowWhenSamePlayer() {
            assertDoesNotThrow(() -> playerService.registerSessionForPlayer(SESSION_ID, PLAYER_ID));
        }

        @Test
        void testUnregisterSession() {
            playerService.unregisterSession(SESSION_ID);

            assertThat(playerService.getDisconnectedPlayers(), contains(player));
        }

        @Test
        void testUnregisterSessionMultipleSessions() {
            playerService.registerSessionForPlayer(2, PLAYER_ID);
            playerService.unregisterSession(SESSION_ID);

            assertThat(playerService.getDisconnectedPlayers(), hasSize(0));
        }

        @Test
        void testAddRemoveFriend() {
            int otherId = 2;
            assertFalse(player.isFriend(otherId));
            playerService.handleSocialRequest(createInboundMessage(new SocialMessage.SocialAddRequest(otherId, null)));

            verify(friendOrFoeRepository).upsertPlayerRelationship(PLAYER_ID, otherId, FriendOrFoeEntity.Status.FRIEND);
            assertTrue(player.isFriend(otherId));

            playerService.handleSocialRequest(
                    createInboundMessage(new SocialMessage.SocialRemoveRequest(otherId, null)));

            verify(friendOrFoeRepository).deletePlayerRelationship(PLAYER_ID, otherId);
            assertFalse(player.isFriend(otherId));
        }

        @Test
        void testAddRemoveFoe() {
            int otherId = 2;
            assertFalse(player.isFoe(otherId));
            playerService.handleSocialRequest(createInboundMessage(new SocialMessage.SocialAddRequest(null, otherId)));

            verify(friendOrFoeRepository).upsertPlayerRelationship(PLAYER_ID, otherId, FriendOrFoeEntity.Status.FOE);
            assertTrue(player.isFoe(otherId));

            playerService.handleSocialRequest(
                    createInboundMessage(new SocialMessage.SocialRemoveRequest(null, otherId)));

            verify(friendOrFoeRepository).deletePlayerRelationship(PLAYER_ID, otherId);
            assertFalse(player.isFoe(otherId));
        }

        @Test
        void testFriendToFoe() {
            int otherId = 2;
            playerService.handleSocialRequest(createInboundMessage(new SocialMessage.SocialAddRequest(otherId, null)));
            assertTrue(player.isFriend(otherId));
            assertFalse(player.isFoe(otherId));

            playerService.handleSocialRequest(createInboundMessage(new SocialMessage.SocialAddRequest(null, otherId)));

            verify(friendOrFoeRepository).upsertPlayerRelationship(PLAYER_ID, otherId, FriendOrFoeEntity.Status.FOE);
            assertTrue(player.isFoe(otherId));
            assertFalse(player.isFriend(otherId));
        }

        @Test
        void testFoeToFriend() {
            int otherId = 2;
            playerService.handleSocialRequest(createInboundMessage(new SocialMessage.SocialAddRequest(null, otherId)));
            assertTrue(player.isFoe(otherId));
            assertFalse(player.isFriend(otherId));

            playerService.handleSocialRequest(createInboundMessage(new SocialMessage.SocialAddRequest(otherId, null)));

            verify(friendOrFoeRepository).upsertPlayerRelationship(PLAYER_ID, otherId, FriendOrFoeEntity.Status.FRIEND);
            assertTrue(player.isFriend(otherId));
            assertFalse(player.isFoe(otherId));
        }

        @Test
        void testSendAvatars() {
            when(assignedAvatarRepository.findAssignedAvatarsByPlayer(PLAYER_ID)).thenReturn(Set.of(AVATAR));

            playerService.handleSocialRequest(createInboundMessage(new SocialMessage.ListAvatarsRequest()));

            List<? extends Message<OutboundLobbyMessage<?>>> sentMessages = testingConnector.<OutboundLobbyMessage<?>>outgoing(
                    LOBBY_OUTBOUND_CHANNEL).sent();
            assertEquals(1, sentMessages.size());

            assertEquals(OutboundLobbyMessage.forSession(SESSION_ID,
                            new SocialMessage.AvatarInfoList(Set.of(new AvatarInfo(AVATAR.url(), AVATAR.description())))),
                    sentMessages.getFirst().getPayload());
        }

        @Test
        void testSelectAvatar() {
            Avatar avatar = new Avatar("new", "newone");

            when(assignedAvatarRepository.updateSelectedAvatar(PLAYER_ID, "new")).thenReturn(avatar);

            playerService.handleSocialRequest(
                    createInboundMessage(new SocialMessage.SelectAvatarRequest(avatar.url())));

            assertThat(player.getAvatar().orElseThrow(), equalTo(avatar));
            assertThat(playerService.getDirtyPlayers(), contains(player));
        }

        @Test
        void testSelectAvatarAlreadySelected() {
            playerService.handleSocialRequest(
                    createInboundMessage(new SocialMessage.SelectAvatarRequest(AVATAR.url())));

            verifyNoInteractions(assignedAvatarRepository);
            assertThat(player.getAvatar().orElseThrow(), equalTo(AVATAR));
            assertThat(playerService.getDirtyPlayers(), hasSize(0));
        }

        @Test
        void testRemoveAvatar() {
            playerService.handleSocialRequest(createInboundMessage(new SocialMessage.RemoveAvatarRequest()));

            verify(assignedAvatarRepository).removeSelectedAvatar(PLAYER_ID);
            assertThat(player.getAvatar(), equalTo(Optional.empty()));
            assertThat(playerService.getDirtyPlayers(), contains(player));
        }

        @Test
        void testRemoveAvatarNoAvatarSelected() {
            player.clearAvatar();
            playerService.handleSocialRequest(createInboundMessage(new SocialMessage.RemoveAvatarRequest()));

            verifyNoInteractions(assignedAvatarRepository);
            assertThat(player.getAvatar(), equalTo(Optional.empty()));
            assertThat(playerService.getDirtyPlayers(), hasSize(0));
        }

        @Test
        void testMarkDirty() {
            playerService.markDirty(player);
            assertThat(playerService.getDirtyPlayers(), contains(player));

            playerService.sendUpdateForDirtyPlayers();

            List<? extends Message<OutboundLobbyMessage<?>>> sentMessages = testingConnector.<OutboundLobbyMessage<?>>outgoing(
                    LOBBY_OUTBOUND_CHANNEL).sent();
            assertEquals(1, sentMessages.size());
            assertEquals(OutboundLobbyMessage.forAll(new SocialMessage.PlayerInfoList(Set.of(PLAYER_INFO))),
                    sentMessages.getFirst().getPayload());

            assertThat(playerService.getDirtyPlayers(), hasSize(0));
        }

        @Test
        void testMarkDisconnected() {
            playerService.markDisconnected(player);
            assertThat(playerService.getDisconnectedPlayers(), contains(player));

            playerService.removeDisconnectedPlayers();

            assertThat(playerService.getDisconnectedPlayers(), hasSize(0));
            assertThat(playerService.getDirtyPlayers(), contains(player));
        }

        @Test
        void testBroadcast() {
            playerService.handleAdminRequest(createInboundMessage(new AdminMessage.BroadcastRequest("test")));

            List<? extends Message<OutboundLobbyMessage<?>>> sentMessages = testingConnector.<OutboundLobbyMessage<?>>outgoing(
                    LOBBY_OUTBOUND_CHANNEL).sent();
            assertEquals(1, sentMessages.size());
            assertEquals(OutboundLobbyMessage.forAll(new AdminMessage.NoticeInfo("test", AdminMessage.Style.INFO)),
                    sentMessages.getFirst().getPayload());
        }

        @Test
        void testBroadcastNoMessage() {
            playerService.handleAdminRequest(createInboundMessage(new AdminMessage.BroadcastRequest("")));

            verifyNoInteractions(userGroupAssignmentRepository);
        }

        @Test
        void testBroadcastNoPermission() {
            when(userGroupAssignmentRepository.playerLacksPermission(PLAYER_ID, "ADMIN_BROADCAST_MESSAGE")).thenReturn(
                    true);

            playerService.handleAdminRequest(createInboundMessage(new AdminMessage.BroadcastRequest("test")));

            List<? extends Message<OutboundLobbyMessage<?>>> sentMessages = testingConnector.<OutboundLobbyMessage<?>>outgoing(
                    LOBBY_OUTBOUND_CHANNEL).sent();
            assertEquals(0, sentMessages.size());
        }

        @Test
        void testKick() {
            playerService.handleAdminRequest(createInboundMessage(new AdminMessage.KickPlayerRequest(2)));

            List<? extends Message<OutboundLobbyMessage<?>>> sentMessages = testingConnector.<OutboundLobbyMessage<?>>outgoing(
                    LOBBY_OUTBOUND_CHANNEL).sent();
            assertEquals(1, sentMessages.size());
            assertEquals(OutboundLobbyMessage.forPlayer(2, new AdminMessage.NoticeInfo(null, AdminMessage.Style.KICK)),
                    sentMessages.getFirst().getPayload());
        }

        @Test
        void testKickNoPermission() {
            when(userGroupAssignmentRepository.playerLacksPermission(PLAYER_ID, "ADMIN_KICK_SERVER")).thenReturn(true);

            playerService.handleAdminRequest(createInboundMessage(new AdminMessage.KickPlayerRequest(2)));

            List<? extends Message<OutboundLobbyMessage<?>>> sentMessages = testingConnector.<OutboundLobbyMessage<?>>outgoing(
                    LOBBY_OUTBOUND_CHANNEL).sent();
            assertEquals(0, sentMessages.size());
        }

        @Test
        void testCloseGame() {
            playerService.handleAdminRequest(createInboundMessage(new AdminMessage.ClosePlayerGameRequest(2)));

            List<? extends Message<OutboundLobbyMessage<?>>> sentMessages = testingConnector.<OutboundLobbyMessage<?>>outgoing(
                    LOBBY_OUTBOUND_CHANNEL).sent();
            assertEquals(1, sentMessages.size());
            assertEquals(OutboundLobbyMessage.forPlayer(2, new AdminMessage.NoticeInfo(null, AdminMessage.Style.KILL)),
                    sentMessages.getFirst().getPayload());
        }

        @Test
        void testCloseGameNoPermission() {
            when(userGroupAssignmentRepository.playerLacksPermission(PLAYER_ID, "ADMIN_KICK_SERVER")).thenReturn(true);

            playerService.handleAdminRequest(createInboundMessage(new AdminMessage.ClosePlayerGameRequest(2)));

            List<? extends Message<OutboundLobbyMessage<?>>> sentMessages = testingConnector.<OutboundLobbyMessage<?>>outgoing(
                    LOBBY_OUTBOUND_CHANNEL).sent();
            assertEquals(0, sentMessages.size());
        }
    }

    private <T extends LobbyMessage.Client> InboundLobbyMessage<T> createInboundMessage(T message) {
        return new InboundLobbyMessage<>(SESSION_ID, PLAYER_ID, message);
    }
}
