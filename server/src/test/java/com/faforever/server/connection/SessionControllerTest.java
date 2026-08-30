package com.faforever.server.connection;

import com.faforever.server.domain.FriendOrFoeEntity;
import com.faforever.server.message.ConnectionMessage;
import com.faforever.server.message.SocialMessage;
import com.faforever.server.player.Player;
import com.faforever.server.social.FriendOrFoeRepository;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

@QuarkusTest
@RequiredArgsConstructor
@TestTransaction
class SessionControllerTest {

    private static final int PLAYER_ID = 1;

    @Inject
    SessionController sessionController;

    @Inject
    FriendOrFoeRepository friendOrFoeRepository;

    private final TestLobbyConnection connection = new TestLobbyConnection();

    @BeforeEach
    void setup() {
        sessionController.setConnection(connection);
    }

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
    void testFriendChange() {
        friendOrFoeRepository.deleteAll();
        sessionController.initializePlayer(PLAYER_ID);
        Player player = sessionController.player().orElseThrow();

        assertThat(player.getFriendIds(), hasSize(0));
        assertThat(player.getFoeIds(), hasSize(0));
        assertThat(friendOrFoeRepository.list("id.playerId", PLAYER_ID), hasSize(0));

        int otherId = 2;
        sessionController.handleMessage(new SocialMessage.SocialAddRequest(otherId, null));

        assertThat(player.getFriendIds(), contains(otherId));
        assertThat(player.getFoeIds(), hasSize(0));
        assertThat(friendOrFoeRepository.findById(new FriendOrFoeEntity.Id(PLAYER_ID, otherId)).getStatus(),
                equalTo(FriendOrFoeEntity.Status.FRIEND));

        sessionController.handleMessage(new SocialMessage.SocialRemoveRequest(otherId, null));

        assertThat(player.getFriendIds(), hasSize(0));
        assertThat(player.getFoeIds(), hasSize(0));
        assertThat(friendOrFoeRepository.list("id.playerId", PLAYER_ID), hasSize(0));
    }

    @Test
    void testFoeChange() {
        friendOrFoeRepository.deleteAll();
        sessionController.initializePlayer(PLAYER_ID);
        Player player = sessionController.player().orElseThrow();

        assertThat(player.getFriendIds(), hasSize(0));
        assertThat(player.getFoeIds(), hasSize(0));
        assertThat(friendOrFoeRepository.list("id.playerId", PLAYER_ID), hasSize(0));

        int otherId = 2;
        sessionController.handleMessage(new SocialMessage.SocialAddRequest(null, otherId));

        assertThat(player.getFoeIds(), contains(otherId));
        assertThat(player.getFriendIds(), hasSize(0));
        assertThat(friendOrFoeRepository.findById(new FriendOrFoeEntity.Id(PLAYER_ID, otherId)).getStatus(),
                equalTo(FriendOrFoeEntity.Status.FOE));

        sessionController.handleMessage(new SocialMessage.SocialRemoveRequest(null, otherId));

        assertThat(player.getFoeIds(), hasSize(0));
        assertThat(player.getFriendIds(), hasSize(0));
        assertThat(friendOrFoeRepository.list("id.playerId", PLAYER_ID), hasSize(0));
    }
}
