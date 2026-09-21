package com.faforever.server.game;

import com.faforever.server.mapstruct.OptionalMapperImpl;
import com.faforever.server.message.MessageBroker;
import com.faforever.server.message.external.GameMessage;
import com.faforever.server.message.external.dto.DtoMapperImpl;
import com.faforever.server.message.external.dto.GameAccess;
import com.faforever.server.message.external.dto.GameType;
import com.faforever.server.message.external.dto.GameVisibility;
import com.faforever.server.message.external.dto.LobbyMode;
import com.faforever.server.message.internal.InboundLobbyMessage;
import com.faforever.server.message.internal.OutboundLobbyMessage;
import com.faforever.server.message.internal.OutboundTarget;
import com.faforever.server.player.Player;
import com.faforever.server.player.PlayerService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
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
    private MessageBroker messageBroker;
    @InjectMock
    private GameRepository gameRepository;

    private final Player player = new Player(PLAYER_ID, "test");

    @BeforeEach
    void setup() {
        when(playerService.getSessionPlayer(SESSION_ID)).thenReturn(player);
        when(gameRepository.findMaxGameId()).thenReturn(0);
    }

    @Test
    void testHostGame() {
        gameService.handleRequest(createInboundMessage(
                new GameMessage.HostGameRequest("scmp_009", "game", "faf", GameAccess.PUBLIC, null,
                        GameVisibility.PUBLIC, null, null, false)));

        ArgumentCaptor<OutboundLobbyMessage<GameMessage.GameLaunchResponse>> captor = captor();
        verify(messageBroker).handleOutboundMessage(captor.capture());

        OutboundLobbyMessage<GameMessage.GameLaunchResponse> message = captor.getValue();
        OutboundTarget target = message.target();
        assertThat(target, isA(OutboundTarget.Session.class));

        OutboundTarget.Session sessionTarget = (OutboundTarget.Session) message.target();
        assertThat(sessionTarget.sessionId(), equalTo(SESSION_ID));

        GameMessage.GameLaunchResponse gameLaunchResponse = message.message();
        assertThat(gameLaunchResponse,
                equalTo(new GameMessage.GameLaunchResponse(1, "game", "faf", LobbyMode.DEFAULT_LOBBY, GameType.CUSTOM,
                        "global", "scmp_009", null, null, Map.of(), null, null)));
    }

    private InboundLobbyMessage<GameMessage.Client> createInboundMessage(GameMessage.Client message) {
        return new InboundLobbyMessage<>(SESSION_ID, PLAYER_ID, message);
    }
}
