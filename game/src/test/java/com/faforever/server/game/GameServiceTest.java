package com.faforever.server.game;

import com.faforever.server.mapstruct.OptionalMapperImpl;
import com.faforever.server.message.client.GameMessage;
import com.faforever.server.message.client.LobbyMessage;
import com.faforever.server.message.client.dto.DtoMapperImpl;
import com.faforever.server.message.client.dto.GameAccess;
import com.faforever.server.message.client.dto.GameType;
import com.faforever.server.message.client.dto.GameVisibility;
import com.faforever.server.message.client.dto.LobbyMode;
import com.faforever.server.message.service.InboundLobbyMessage;
import com.faforever.server.message.service.OutboundLobbyMessage;
import com.faforever.server.message.service.OutboundTarget;
import com.faforever.server.player.Player;
import com.faforever.server.player.PlayerService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.isA;
import static org.mockito.ArgumentCaptor.captor;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusComponentTest(value = {DtoMapperImpl.class, OptionalMapperImpl.class})
public class GameServiceTest {

    private static final int PLAYER_ID = 1;
    private static final long SESSION_ID = 0;

    @Inject
    GameService gameService;

    @InjectMock
    private PlayerService playerService;
    @InjectMock
    private Emitter<OutboundLobbyMessage<? extends LobbyMessage.Server>> messageBroker;
    @InjectMock
    private GameRepository gameRepository;

    private final Player player = new Player(PLAYER_ID, "test");

    @BeforeEach
    void setup() {
        when(gameRepository.findMaxGameId()).thenReturn(0);
    }

    @Test
    void testHostGame() {
        String mapname = "scmp_009";
        String title = "game";
        String featuredMod = "faf";
        gameService.handleRequest(createInboundMessage(
                new GameMessage.HostGameRequest(mapname, title, featuredMod, new boolean[0], 0, GameAccess.PUBLIC, null,
                        GameVisibility.PUBLIC, null, null, false)));

        ArgumentCaptor<OutboundLobbyMessage<GameMessage.GameLaunchResponse>> captor = captor();
        verify(messageBroker).send(captor.capture());

        OutboundLobbyMessage<GameMessage.GameLaunchResponse> message = captor.getValue();
        OutboundTarget target = message.target();
        assertThat(target, isA(OutboundTarget.Session.class));

        OutboundTarget.Session sessionTarget = (OutboundTarget.Session) message.target();
        assertThat(sessionTarget.sessionId(), equalTo(SESSION_ID));

        GameMessage.GameLaunchResponse gameLaunchResponse = message.message();
        assertThat(gameLaunchResponse,
                equalTo(new GameMessage.GameLaunchResponse(1, title, featuredMod, LobbyMode.DEFAULT_LOBBY, GameType.CUSTOM,
                        "global", mapname, null, null, Map.of(), null, null)));
    }

    private InboundLobbyMessage<GameMessage.Client> createInboundMessage(GameMessage.Client message) {
        return new InboundLobbyMessage<>(SESSION_ID, PLAYER_ID, message);
    }
}
