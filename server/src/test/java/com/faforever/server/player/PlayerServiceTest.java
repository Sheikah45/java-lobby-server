package com.faforever.server.player;

import com.faforever.server.domain.FriendOrFoeEntity;
import com.faforever.server.game.GameService;
import com.faforever.server.mapstruct.OptionalMapperImpl;
import com.faforever.server.message.MessageBroker;
import com.faforever.server.message.external.ConnectionMessage;
import com.faforever.server.message.external.LobbyMessage;
import com.faforever.server.message.external.SocialMessage;
import com.faforever.server.message.external.dto.AvatarInfo;
import com.faforever.server.message.external.dto.DtoMapperImpl;
import com.faforever.server.message.external.dto.LeaderboardStats;
import com.faforever.server.message.external.dto.PlayerInfo;
import com.faforever.server.message.internal.InboundLobbyMessage;
import com.faforever.server.message.internal.OutboundLobbyMessage;
import com.faforever.server.message.internal.OutboundTarget;
import com.faforever.server.rating.Leaderboard;
import com.faforever.server.rating.LeaderboardRating;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentCaptor.captor;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@QuarkusComponentTest(value = {DtoMapperImpl.class, OptionalMapperImpl.class})
class PlayerServiceTest {

    private static final int PLAYER_ID = 1;
    private static final long SESSION_ID = 0;
    private static final PlayerInfo PLAYER_INFO = new PlayerInfo(PLAYER_ID, "test", "TEST",
            new AvatarInfo("temp", "temporary"), null,
            Map.of("leaderboard", new LeaderboardStats(1, new LeaderboardStats.Rating(1000, 100))), null);
    private static final Avatar AVATAR = new Avatar("temp", "temporary");

    @Inject
    PlayerService playerService;

    @InjectMock
    private GameService gameService;
    @InjectMock
    private MessageBroker messageBroker;
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
        player.setClan("TEST");
        player.setAvatar(AVATAR);
        player.setLeaderboardRatings(List.of(new LeaderboardRating(new Leaderboard("leaderboard"), 1, 1000, 100)));

        when(playerRepository.loadPlayer(PLAYER_ID)).thenReturn(player);
    }

    @Nested
    class Unregistered {

        @Test
        void testRegisterSession() {
            playerService.registerSessionForPlayer(SESSION_ID, PLAYER_ID);

            assertThat(playerService.getSessionPlayer(SESSION_ID), equalTo(player));
            assertThat(playerService.getDirtyPlayers(), contains(player));

            ArgumentCaptor<OutboundLobbyMessage<?>> messageRequestCaptor = captor();
            verify(messageBroker, times(3)).handleOutboundMessage(messageRequestCaptor.capture());

            List<OutboundLobbyMessage<?>> sentMessages = messageRequestCaptor.getAllValues();

            OutboundLobbyMessage<?> firstMessage = sentMessages.getFirst();
            OutboundTarget firstTarget = firstMessage.target();
            assertThat(firstTarget, isA(OutboundTarget.Session.class));

            OutboundTarget.Session firstSessionTarget = (OutboundTarget.Session) firstTarget;
            assertThat(firstSessionTarget.sessionId(), equalTo(SESSION_ID));

            assertThat(firstMessage.message(), isA(ConnectionMessage.LoginSuccessResponse.class));

            ConnectionMessage.LoginSuccessResponse loginSuccessResponse = (ConnectionMessage.LoginSuccessResponse) firstMessage.message();
            assertThat(loginSuccessResponse.currentTime(), lessThanOrEqualTo(OffsetDateTime.now()));
            assertThat(loginSuccessResponse.me(), equalTo(PLAYER_INFO));

            OutboundLobbyMessage<?> secondMessage = sentMessages.get(1);
            OutboundTarget secondTarget = secondMessage.target();
            assertThat(secondTarget, isA(OutboundTarget.Session.class));

            OutboundTarget.Session secondSessionTarget = (OutboundTarget.Session) secondTarget;

            assertThat(secondSessionTarget.sessionId(), equalTo(SESSION_ID));
            assertThat(secondMessage.message(),
                    equalTo(new SocialMessage.SocialInfo(Set.of("#TEST_clan"), Set.of(), Set.of())));

            OutboundLobbyMessage<?> thirdMessage = sentMessages.getLast();
            OutboundTarget thirdTarget = thirdMessage.target();
            assertThat(thirdTarget, isA(OutboundTarget.Session.class));

            OutboundTarget.Session thirdSessionTarget = (OutboundTarget.Session) thirdTarget;

            assertThat(thirdSessionTarget.sessionId(), equalTo(SESSION_ID));
            assertThat(thirdMessage.message(), isA(SocialMessage.PlayerInfoList.class));

            assertThat(playerService.getOnlinePlayers(), contains(player));

            verify(gameService).sendGamesToSession(SESSION_ID);
        }

        @Test
        void testRegisterSessionAsModerator() {
            when(userGroupAssignmentRepository.isPlayerModerator(PLAYER_ID)).thenReturn(true);

            playerService.registerSessionForPlayer(SESSION_ID, PLAYER_ID);

            ArgumentCaptor<OutboundLobbyMessage<?>> messageRequestCaptor = captor();
            verify(messageBroker, times(3)).handleOutboundMessage(messageRequestCaptor.capture());

            List<OutboundLobbyMessage<?>> sentMessages = messageRequestCaptor.getAllValues();

            OutboundLobbyMessage<?> secondMessage = sentMessages.get(1);
            OutboundTarget secondTarget = secondMessage.target();
            assertThat(secondTarget, isA(OutboundTarget.Session.class));

            OutboundTarget.Session secondSessionTarget = (OutboundTarget.Session) secondTarget;

            assertThat(secondSessionTarget.sessionId(), equalTo(SESSION_ID));
            assertThat(secondMessage.message(),
                    equalTo(new SocialMessage.SocialInfo(Set.of("#TEST_clan", "#moderators"), Set.of(), Set.of())));
        }

        @Test
        void testChangeSocialRelationshipOfflinePlayer() {
            playerService.handleRequest(createInboundMessage(new SocialMessage.SocialAddRequest(2, 3)));
            playerService.handleRequest(createInboundMessage(new SocialMessage.SocialRemoveRequest(2, 3)));

            assertThat(player.getFriendIds(), hasSize(0));
            assertThat(player.getFoeIds(), hasSize(0));
            verifyNoInteractions(friendOrFoeRepository);
        }

        @Test
        void testRemoveAvatarOfflinePlayer() {
            playerService.handleRequest(createInboundMessage(new SocialMessage.RemoveAvatarRequest()));

            assertThat(player.getAvatar().orElseThrow(), equalTo(AVATAR));
            assertThat(playerService.getDirtyPlayers(), hasSize(0));
            verifyNoInteractions(assignedAvatarRepository);
        }

        @Test
        void testSelectAvatarOfflinePlayer() {
            playerService.handleRequest(createInboundMessage(new SocialMessage.SelectAvatarRequest("test")));

            assertThat(player.getAvatar().orElseThrow(), equalTo(AVATAR));
            assertThat(playerService.getDirtyPlayers(), hasSize(0));
            verifyNoInteractions(assignedAvatarRepository);
        }

        @Test
        void testSendAvatarsOfflinePlayer() {
            playerService.handleRequest(createInboundMessage(new SocialMessage.ListAvatarsRequest()));

            verifyNoInteractions(assignedAvatarRepository, messageBroker);
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
            verifyNoInteractions(messageBroker);
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
    }

    @Nested
    class Registered {

        @BeforeEach
        void setup() {
            playerService.registerSessionForPlayer(SESSION_ID, PLAYER_ID);
            playerService.getDirtyPlayers().clear();
            clearInvocations(messageBroker);
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
            playerService.handleRequest(createInboundMessage(new SocialMessage.SocialAddRequest(otherId, null)));

            verify(friendOrFoeRepository).upsertPlayerRelationship(PLAYER_ID, otherId, FriendOrFoeEntity.Status.FRIEND);
            assertTrue(player.isFriend(otherId));

            playerService.handleRequest(createInboundMessage(new SocialMessage.SocialRemoveRequest(otherId, null)));

            verify(friendOrFoeRepository).deletePlayerRelationship(PLAYER_ID, otherId);
            assertFalse(player.isFriend(otherId));
        }

        @Test
        void testAddRemoveFoe() {
            int otherId = 2;
            assertFalse(player.isFoe(otherId));
            playerService.handleRequest(createInboundMessage(new SocialMessage.SocialAddRequest(null, otherId)));

            verify(friendOrFoeRepository).upsertPlayerRelationship(PLAYER_ID, otherId, FriendOrFoeEntity.Status.FOE);
            assertTrue(player.isFoe(otherId));

            playerService.handleRequest(createInboundMessage(new SocialMessage.SocialRemoveRequest(null, otherId)));

            verify(friendOrFoeRepository).deletePlayerRelationship(PLAYER_ID, otherId);
            assertFalse(player.isFoe(otherId));
        }

        @Test
        void testFriendToFoe() {
            int otherId = 2;
            playerService.handleRequest(createInboundMessage(new SocialMessage.SocialAddRequest(otherId, null)));
            assertTrue(player.isFriend(otherId));
            assertFalse(player.isFoe(otherId));

            playerService.handleRequest(createInboundMessage(new SocialMessage.SocialAddRequest(null, otherId)));

            verify(friendOrFoeRepository).upsertPlayerRelationship(PLAYER_ID, otherId, FriendOrFoeEntity.Status.FOE);
            assertTrue(player.isFoe(otherId));
            assertFalse(player.isFriend(otherId));
        }

        @Test
        void testFoeToFriend() {
            int otherId = 2;
            playerService.handleRequest(createInboundMessage(new SocialMessage.SocialAddRequest(null, otherId)));
            assertTrue(player.isFoe(otherId));
            assertFalse(player.isFriend(otherId));

            playerService.handleRequest(createInboundMessage(new SocialMessage.SocialAddRequest(otherId, null)));

            verify(friendOrFoeRepository).upsertPlayerRelationship(PLAYER_ID, otherId, FriendOrFoeEntity.Status.FRIEND);
            assertTrue(player.isFriend(otherId));
            assertFalse(player.isFoe(otherId));
        }

        @Test
        void testSendAvatars() {
            when(assignedAvatarRepository.findAssignedAvatarsByPlayer(PLAYER_ID)).thenReturn(Set.of(AVATAR));

            playerService.handleRequest(createInboundMessage(new SocialMessage.ListAvatarsRequest()));

            verify(messageBroker).handleOutboundMessage(OutboundLobbyMessage.forSession(SESSION_ID,
                    new SocialMessage.AvatarInfoList(List.of(new AvatarInfo(AVATAR.url(), AVATAR.description())))));
        }

        @Test
        void testSelectAvatar() {
            Avatar avatar = new Avatar("new", "newone");

            when(assignedAvatarRepository.updateSelectedAvatar(PLAYER_ID, "new")).thenReturn(avatar);

            playerService.handleRequest(createInboundMessage(new SocialMessage.SelectAvatarRequest(avatar.url())));

            assertThat(player.getAvatar().orElseThrow(), equalTo(avatar));
            assertThat(playerService.getDirtyPlayers(), contains(player));
        }

        @Test
        void testSelectAvatarAlreadySelected() {
            playerService.handleRequest(createInboundMessage(new SocialMessage.SelectAvatarRequest(AVATAR.url())));

            verifyNoInteractions(assignedAvatarRepository);
            assertThat(player.getAvatar().orElseThrow(), equalTo(AVATAR));
            assertThat(playerService.getDirtyPlayers(), hasSize(0));
        }

        @Test
        void testRemoveAvatar() {
            playerService.handleRequest(createInboundMessage(new SocialMessage.RemoveAvatarRequest()));

            verify(assignedAvatarRepository).removeSelectedAvatar(PLAYER_ID);
            assertThat(player.getAvatar(), equalTo(Optional.empty()));
            assertThat(playerService.getDirtyPlayers(), contains(player));
        }

        @Test
        void testRemoveAvatarNoAvatarSelected() {
            player.clearAvatar();
            playerService.handleRequest(createInboundMessage(new SocialMessage.RemoveAvatarRequest()));

            verifyNoInteractions(assignedAvatarRepository);
            assertThat(player.getAvatar(), equalTo(Optional.empty()));
            assertThat(playerService.getDirtyPlayers(), hasSize(0));
        }

        @Test
        void testMarkDirty() {
            playerService.markDirty(player);
            assertThat(playerService.getDirtyPlayers(), contains(player));

            playerService.sendUpdateForDirtyPlayers();

            verify(messageBroker).handleOutboundMessage(
                    OutboundLobbyMessage.forAll(new SocialMessage.PlayerInfoList(Set.of(PLAYER_INFO))));
            assertThat(playerService.getDirtyPlayers(), hasSize(0));
        }

        @Test
        void testMarkDisconnected() {
            playerService.markDisconnected(player);
            assertThat(playerService.getDisconnectedPlayers(), contains(player));

            playerService.removeDisconnectedPlayers();

            assertThat(playerService.getDisconnectedPlayers(), hasSize(0));
            assertThat(playerService.getDirtyPlayers(), contains(player));
            assertThrows(IllegalArgumentException.class, () -> playerService.getSessionPlayer(PLAYER_ID));
        }
    }

    private <T extends LobbyMessage.Authenticated> InboundLobbyMessage<T> createInboundMessage(T message) {
        return new InboundLobbyMessage<>(SESSION_ID, PLAYER_ID, message);
    }
}
