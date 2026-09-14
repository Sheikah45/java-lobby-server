package com.faforever.server.admin;

import com.faforever.server.game.GameService;
import com.faforever.server.message.MessageEmitter;
import com.faforever.server.message.external.AdminMessage;
import com.faforever.server.message.external.LobbyMessage;
import com.faforever.server.message.internal.MessageRequest;
import com.faforever.server.player.Player;
import com.faforever.server.player.PlayerService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@QuarkusComponentTest
public class AdminServiceTest {

    private static final long SESSION_ID = 0;

    @Inject AdminService adminService;

    @InjectMock
    private PlayerService playerService;
    @InjectMock
    private GameService gameService;

    @InjectMock
    private MessageEmitter messageEmitter;

    @Test
    void testBroadcast() {
        adminService.handleRequest(new AdminRequest.Broadcast(SESSION_ID, "test"));

        verify(playerService).broadcast(new AdminMessage.NoticeInfo("test", AdminMessage.Style.INFO));
    }

    @Test
    void testBroadcastNoMessage() {
        adminService.handleRequest(new AdminRequest.Broadcast(SESSION_ID, ""));

        verifyNoInteractions(playerService);
    }

    @Test
    void testBroadcastNoPermission() {
        when(playerService.sessionLacksPermission(SESSION_ID, "ADMIN_BROADCAST_MESSAGE")).thenReturn(true);
        
        adminService.handleRequest(new AdminRequest.Broadcast(SESSION_ID, "test"));

        verify(playerService, never()).broadcast(any(LobbyMessage.Broadcast.class));
    }

    @Test
    void testKick() {
        adminService.handleRequest(new AdminRequest.KickPlayer(SESSION_ID, 1));

        verify(messageEmitter).send(new MessageRequest.KickPlayer(1));
    }

    @Test
    void testKickNoPermission() {
        when(playerService.sessionLacksPermission(SESSION_ID, "ADMIN_KICK_SERVER")).thenReturn(true);

        adminService.handleRequest(new AdminRequest.KickPlayer(SESSION_ID, 1));

        verifyNoInteractions(messageEmitter);
    }

    @Test
    void testCloseGame() {
        adminService.handleRequest(new AdminRequest.ClosePlayerGame(SESSION_ID, 1));

        verify(gameService).closePlayerGame(1);
    }

    @Test
    void testCloseGameNoPermission() {
        when(playerService.sessionLacksPermission(SESSION_ID, "ADMIN_KICK_SERVER")).thenReturn(true);

        adminService.handleRequest(new AdminRequest.ClosePlayerGame(SESSION_ID, 1));

        verifyNoInteractions(gameService);
    }

}
