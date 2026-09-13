package com.faforever.server.player;

import com.faforever.server.broadcast.BroadcastService;
import com.faforever.server.connection.SessionController;
import com.faforever.server.connection.SessionControllerTestUtils;
import com.faforever.server.connection.TestLobbyConnection;
import com.faforever.server.domain.FriendOrFoeEntity;
import com.faforever.server.mapstruct.OptionalMapperImpl;
import com.faforever.server.message.AdminMessage;
import com.faforever.server.message.ConnectionMessage;
import com.faforever.server.message.LobbyMessage;
import com.faforever.server.message.SocialMessage;
import com.faforever.server.message.dto.AvatarInfo;
import com.faforever.server.message.dto.DtoMapperImpl;
import com.faforever.server.message.dto.LeaderboardStats;
import com.faforever.server.message.dto.PlayerInfo;
import com.faforever.server.rating.Leaderboard;
import com.faforever.server.rating.LeaderboardRating;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@QuarkusComponentTest(value = {DtoMapperImpl.class, OptionalMapperImpl.class})
class PlayerServiceTest {

    private static final int PLAYER_ID = 1;
    private static final PlayerInfo PLAYER_INFO = new PlayerInfo(PLAYER_ID, "test", "TEST",
            new AvatarInfo("temp", "temporary"), "",
            Map.of("leaderboard", new LeaderboardStats(1, new LeaderboardStats.Rating(1000, 100))),
            null);
    private static final Avatar AVATAR = new Avatar("temp", "temporary");

    @Inject
    PlayerService playerService;
    @Inject
    SessionController sessionController;

    @InjectMock
    private BroadcastService broadcastService;

    @InjectMock
    private PlayerRepository playerRepository;
    @InjectMock
    private FriendOrFoeRepository friendOrFoeRepository;
    @InjectMock
    private AssignedAvatarRepository assignedAvatarRepository;

    private final Player player = new Player(PLAYER_ID, "test");
    private final TestLobbyConnection testConnection = new TestLobbyConnection();

    @BeforeEach
    void setup() {
        player.setClan("TEST");
        player.setAvatar(AVATAR);
        player.setLeaderboardRatings(List.of(new LeaderboardRating(new Leaderboard("leaderboard"), 1, 1000, 100)));

        sessionController.setConnection(testConnection);

        when(playerRepository.loadPlayer(PLAYER_ID)).thenReturn(player);
    }

    @Nested
    class Unregistered {

        @Test
        void testRegisterSession() {
            SessionControllerTestUtils.setSessionPlayer(sessionController, PLAYER_ID);

            playerService.registerSession(sessionController);

            assertThat(playerService.getOnlinePlayer(PLAYER_ID), equalTo(player));
            assertThat(playerService.getDirtyPlayers(), contains(player));

            List<LobbyMessage.Server> sentMessages = testConnection.getSentMessages();
            assertThat(sentMessages, hasSize(3));

            LobbyMessage.Server firstMessage = sentMessages.getFirst();
            assertThat(firstMessage, isA(ConnectionMessage.LoginSuccessResponse.class));
            ConnectionMessage.LoginSuccessResponse loginSuccessResponse = (ConnectionMessage.LoginSuccessResponse) firstMessage;
            assertThat(loginSuccessResponse.currentTime(), lessThanOrEqualTo(OffsetDateTime.now()));
            assertThat(loginSuccessResponse.me(),
                    equalTo(PLAYER_INFO));

            LobbyMessage.Server secondMessage = sentMessages.get(1);
            assertThat(secondMessage, equalTo(new SocialMessage.SocialInfo(Set.of("#TEST_clan"), Set.of(), Set.of())));

            LobbyMessage.Server thirdMessage = sentMessages.getLast();
            assertThat(thirdMessage, isA(SocialMessage.PlayerInfoList.class));
        }

        @Test
        void testKickOfflinePlayer() {
            playerService.kickPlayer(PLAYER_ID);

            assertFalse(testConnection.closed());
        }

        @Test
        void testChangeSocialRelationshiipOffliinePlayer() {
            playerService.changeSocialRelationship(new SocialRequest.FriendOrFoe.Add(PLAYER_ID, 2, SocialRequest.FriendOrFoe.Status.FRIEND));
            playerService.changeSocialRelationship(new SocialRequest.FriendOrFoe.Add(PLAYER_ID, 3, SocialRequest.FriendOrFoe.Status.FOE));
            playerService.changeSocialRelationship(new SocialRequest.FriendOrFoe.Remove(PLAYER_ID, 4));

            assertThat(player.getFriendIds(), hasSize(0));
            assertThat(player.getFoeIds(), hasSize(0));
            verifyNoInteractions(friendOrFoeRepository);
        }

        @Test
        void testRemoveAvatarOfflinePlayer() {
            playerService.removeAvatar(new SocialRequest.RemoveAvatar(PLAYER_ID));

            assertThat(player.getAvatar().orElseThrow(), equalTo(AVATAR));
            assertThat(playerService.getDirtyPlayers(), hasSize(0));
            verifyNoInteractions(assignedAvatarRepository);
        }

        @Test
        void testSelectAvatarOfflinePlayer() {
            playerService.selectAvatar(new SocialRequest.SelectAvatar(PLAYER_ID, "test"));

            assertThat(player.getAvatar().orElseThrow(), equalTo(AVATAR));
            assertThat(playerService.getDirtyPlayers(), hasSize(0));
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
            verifyNoInteractions(broadcastService);
        }

        @Test
        void testNoActionWhenNoDisconnected() {
            assertThat(playerService.getDisconnectedPlayers(), hasSize(0));
            playerService.removeDisconnectedPlayers();
            assertThat(playerService.getDisconnectedPlayers(), hasSize(0));
            assertThat(playerService.getDirtyPlayers(), hasSize(0));
        }
    }

    @Nested
    class Registered {

        @BeforeEach
        void setup() {
            SessionControllerTestUtils.setSessionPlayer(sessionController, PLAYER_ID);
            playerService.registerSession(sessionController);
            playerService.getDirtyPlayers().clear();
            testConnection.reset();
        }

        @Test
        void testUnregisterSession() {
            playerService.unregisterSession(sessionController);

            assertThat(playerService.getDisconnectedPlayers(), contains(player));
        }

        @Test
        void testAddRemoveFriend() {
            int otherId = 2;
            assertFalse(player.isFriend(otherId));
            playerService.changeSocialRelationship(
                    new SocialRequest.FriendOrFoe.Add(PLAYER_ID, otherId, SocialRequest.FriendOrFoe.Status.FRIEND));

            verify(friendOrFoeRepository).upsertPlayerRelationship(PLAYER_ID, otherId,
                    FriendOrFoeEntity.Status.FRIEND);
            assertTrue(player.isFriend(otherId));

            playerService.changeSocialRelationship(new SocialRequest.FriendOrFoe.Remove(PLAYER_ID, otherId));

            verify(friendOrFoeRepository).deletePlayerRelationship(PLAYER_ID, otherId);
            assertFalse(player.isFriend(otherId));
        }

        @Test
        void testAddRemoveFoe() {
            int otherId = 2;
            assertFalse(player.isFoe(otherId));
            playerService.changeSocialRelationship(
                    new SocialRequest.FriendOrFoe.Add(PLAYER_ID, otherId, SocialRequest.FriendOrFoe.Status.FOE));

            verify(friendOrFoeRepository).upsertPlayerRelationship(PLAYER_ID, otherId, FriendOrFoeEntity.Status.FOE);
            assertTrue(player.isFoe(otherId));

            playerService.changeSocialRelationship(new SocialRequest.FriendOrFoe.Remove(PLAYER_ID, otherId));

            verify(friendOrFoeRepository).deletePlayerRelationship(PLAYER_ID, otherId);
            assertFalse(player.isFoe(otherId));
        }

        @Test
        void testFriendToFoe() {
            int otherId = 2;
            playerService.changeSocialRelationship(
                    new SocialRequest.FriendOrFoe.Add(PLAYER_ID, otherId, SocialRequest.FriendOrFoe.Status.FRIEND));
            assertTrue(player.isFriend(otherId));
            assertFalse(player.isFoe(otherId));

            playerService.changeSocialRelationship(new SocialRequest.FriendOrFoe.Add(PLAYER_ID, otherId, SocialRequest.FriendOrFoe.Status.FOE));

            verify(friendOrFoeRepository).upsertPlayerRelationship(PLAYER_ID, otherId, FriendOrFoeEntity.Status.FOE);
            assertTrue(player.isFoe(otherId));
            assertFalse(player.isFriend(otherId));
        }

        @Test
        void testFoeToFriend() {
            int otherId = 2;
            playerService.changeSocialRelationship(
                    new SocialRequest.FriendOrFoe.Add(PLAYER_ID, otherId, SocialRequest.FriendOrFoe.Status.FOE));
            assertTrue(player.isFoe(otherId));
            assertFalse(player.isFriend(otherId));

            playerService.changeSocialRelationship(new SocialRequest.FriendOrFoe.Add(PLAYER_ID, otherId, SocialRequest.FriendOrFoe.Status.FRIEND));

            verify(friendOrFoeRepository).upsertPlayerRelationship(PLAYER_ID, otherId, FriendOrFoeEntity.Status.FRIEND);
            assertTrue(player.isFriend(otherId));
            assertFalse(player.isFoe(otherId));
        }

        @Test
        void testGetAvatars() {
            Avatar avatar = AVATAR;
            when(assignedAvatarRepository.findAssignedAvatarsByPlayer(PLAYER_ID)).thenReturn(Set.of(avatar));

            assertThat(playerService.getAvatars(new SocialRequest.Avatars(PLAYER_ID)), contains(avatar));
        }

        @Test
        void testSelectAvatar() {
            Avatar avatar = new Avatar("new", "newone");

            when(assignedAvatarRepository.updateSelectedAvatar(PLAYER_ID, "new")).thenReturn(avatar);

            playerService.selectAvatar(new SocialRequest.SelectAvatar(PLAYER_ID, avatar.url()));

            assertThat(player.getAvatar().orElseThrow(), equalTo(avatar));
            assertThat(playerService.getDirtyPlayers(), contains(player));
        }

        @Test
        void testSelectAvatarAlreadySelected() {
            playerService.selectAvatar(new SocialRequest.SelectAvatar(PLAYER_ID, AVATAR.url()));

            verifyNoInteractions(assignedAvatarRepository);
            assertThat(player.getAvatar().orElseThrow(), equalTo(AVATAR));
            assertThat(playerService.getDirtyPlayers(), hasSize(0));
        }

        @Test
        void testRemoveAvatar() {
            playerService.removeAvatar(new SocialRequest.RemoveAvatar(PLAYER_ID));

            verify(assignedAvatarRepository).removeSelectedAvatar(PLAYER_ID);
            assertThat(player.getAvatar(), equalTo(Optional.empty()));
            assertThat(playerService.getDirtyPlayers(), contains(player));
        }

        @Test
        void testRemoveAvatarNoAvatarSelected() {
            player.clearAvatar();
            playerService.removeAvatar(new SocialRequest.RemoveAvatar(PLAYER_ID));

            verifyNoInteractions(assignedAvatarRepository);
            assertThat(player.getAvatar(), equalTo(Optional.empty()));
            assertThat(playerService.getDirtyPlayers(), hasSize(0));
        }

        @Test
        void testKickPlayer() {
            playerService.kickPlayer(PLAYER_ID);

            List<LobbyMessage.Server> sentMessages = testConnection.getSentMessages();
            assertThat(sentMessages, hasSize(1));

            LobbyMessage.Server firstMessage = sentMessages.getFirst();
            assertThat(firstMessage, equalTo(new AdminMessage.NoticeInfo(null, AdminMessage.Style.KICK)));
        }

        @Test
        void testMarkDirty() {
            playerService.markDirty(player);
            assertThat(playerService.getDirtyPlayers(), contains(player));

            playerService.sendUpdateForDirtyPlayers();

            verify(broadcastService).broadcast(new SocialMessage.PlayerInfoList(Set.of(PLAYER_INFO)));
            assertThat(playerService.getDirtyPlayers(), hasSize(0));
        }

        @Test
        void testMarkDisconnected() {
            playerService.markDisconnected(player);
            assertThat(playerService.getDisconnectedPlayers(), contains(player));

            playerService.removeDisconnectedPlayers();

            assertThat(playerService.getDisconnectedPlayers(), hasSize(0));
            assertThat(playerService.getDirtyPlayers(), contains(player));
            assertThrows(IllegalArgumentException.class, () -> playerService.getOnlinePlayer(PLAYER_ID));
        }
    }
}
