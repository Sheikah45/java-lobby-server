package com.faforever.server.player;

import com.faforever.server.message.MessageBroker;
import com.faforever.server.message.external.AdminMessage;
import com.faforever.server.message.internal.InboundLobbyMessage;
import com.faforever.server.message.internal.OutboundLobbyMessage;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@QuarkusComponentTest
public class AdminServiceTest {

    private static final long SESSION_ID = 0;
    private static final int PLAYER_ID = 1;

    @Inject
    AdminService adminService;

    @InjectMock
    private UserGroupAssignmentRepository userGroupAssignmentRepository;

    @InjectMock
    private MessageBroker messageBroker;

    @Test
    void testBroadcast() {
        adminService.handleRequest(createInboundMessage(new AdminMessage.BroadcastRequest("test")));

        verify(messageBroker).handleOutboundMessage(OutboundLobbyMessage.forAll(new AdminMessage.NoticeInfo("test", AdminMessage.Style.INFO)));
    }

    @Test
    void testBroadcastNoMessage() {
        adminService.handleRequest(createInboundMessage(new AdminMessage.BroadcastRequest("")));

        verifyNoInteractions(userGroupAssignmentRepository);
    }

    @Test
    void testBroadcastNoPermission() {
        when(userGroupAssignmentRepository.playerLacksPermission(PLAYER_ID, "ADMIN_BROADCAST_MESSAGE")).thenReturn(true);
        
        adminService.handleRequest(createInboundMessage(new AdminMessage.BroadcastRequest("test")));

        verifyNoInteractions(messageBroker);
    }

    @Test
    void testKick() {
        adminService.handleRequest(createInboundMessage(new AdminMessage.KickPlayerRequest(2)));

        verify(messageBroker).handleOutboundMessage(OutboundLobbyMessage.forPlayer(2, new AdminMessage.NoticeInfo(null, AdminMessage.Style.KICK)));
    }

    @Test
    void testKickNoPermission() {
        when(userGroupAssignmentRepository.playerLacksPermission(PLAYER_ID, "ADMIN_KICK_SERVER")).thenReturn(true);

        adminService.handleRequest(createInboundMessage(new AdminMessage.KickPlayerRequest(2)));

        verifyNoInteractions(messageBroker);
    }

    @Test
    void testCloseGame() {
        adminService.handleRequest(createInboundMessage(new AdminMessage.ClosePlayerGameRequest(2)));

        verify(messageBroker).handleOutboundMessage(OutboundLobbyMessage.forPlayer(2, new AdminMessage.NoticeInfo(null, AdminMessage.Style.KILL)));
    }

    @Test
    void testCloseGameNoPermission() {
        when(userGroupAssignmentRepository.playerLacksPermission(PLAYER_ID, "ADMIN_KICK_SERVER")).thenReturn(true);

        adminService.handleRequest(createInboundMessage(new AdminMessage.ClosePlayerGameRequest(2)));

        verifyNoInteractions(messageBroker);
    }

    private InboundLobbyMessage<AdminMessage.Client> createInboundMessage(AdminMessage.Client message) {
        return new InboundLobbyMessage<>(SESSION_ID, PLAYER_ID, message);
    }
}
