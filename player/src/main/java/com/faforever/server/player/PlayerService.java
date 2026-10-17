package com.faforever.server.player;

import com.faforever.server.domain.FriendOrFoeEntity;
import com.faforever.server.message.client.AdminMessage;
import com.faforever.server.message.client.LobbyMessage;
import com.faforever.server.message.client.SocialMessage;
import com.faforever.server.message.client.dto.AvatarInfo;
import com.faforever.server.message.service.InboundLobbyMessage;
import com.faforever.server.message.service.OutboundLobbyMessage;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.jbosslog.JBossLog;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import java.util.Set;
import java.util.stream.Collectors;

@JBossLog
@ApplicationScoped
public class PlayerService {

    private final PlayerStateTracker playerStateTracker;
    private final Emitter<OutboundLobbyMessage<LobbyMessage.Server>> outboundLobbyMessageEmitter;

    private final FriendOrFoeRepository friendOrFoeRepository;
    private final AssignedAvatarRepository assignedAvatarRepository;
    private final UserGroupAssignmentRepository userGroupAssignmentRepository;

    PlayerService(PlayerStateTracker playerStateTracker,
                  @Channel("lobby-outbound") Emitter<OutboundLobbyMessage<LobbyMessage.Server>> outboundLobbyMessageEmitter,
                  FriendOrFoeRepository friendOrFoeRepository, AssignedAvatarRepository assignedAvatarRepository,
                  UserGroupAssignmentRepository userGroupAssignmentRepository) {
        this.playerStateTracker = playerStateTracker;
        this.outboundLobbyMessageEmitter = outboundLobbyMessageEmitter;
        this.friendOrFoeRepository = friendOrFoeRepository;
        this.assignedAvatarRepository = assignedAvatarRepository;
        this.userGroupAssignmentRepository = userGroupAssignmentRepository;
    }

    @Incoming("admin-inbound")
    public void handleAdminRequest(InboundLobbyMessage<AdminMessage.Client> request) {
        switch (request.message()) {
            case AdminMessage.BroadcastRequest broadcastRequest -> broadcast(request.playerId(), broadcastRequest);
            case AdminMessage.KickPlayerRequest kickPlayerRequest -> kickPlayer(request.playerId(), kickPlayerRequest);
            case AdminMessage.ClosePlayerGameRequest closePlayerGameRequest -> closePlayerGame(request.playerId(),
                                                                                               closePlayerGameRequest);
        }
    }

    private void broadcast(int playerId, AdminMessage.BroadcastRequest broadcastRequest) {
        String message = broadcastRequest.message();
        if (message.isBlank()) {
            return;
        }

        if (userGroupAssignmentRepository.playerLacksPermission(playerId, "ADMIN_BROADCAST_MESSAGE")) {
            LOG.warnf("Unauthorized broadcast request: %s", broadcastRequest.message());
            return;
        }

        outboundLobbyMessageEmitter.send(
                OutboundLobbyMessage.forAll(new AdminMessage.NoticeInfo(message, AdminMessage.Style.INFO)));
    }

    private void kickPlayer(int playerId, AdminMessage.KickPlayerRequest kickPlayerRequest) {
        if (userGroupAssignmentRepository.playerLacksPermission(playerId, "ADMIN_KICK_SERVER")) {
            LOG.warnf("Unauthorized kick request with target: %d", kickPlayerRequest.playerId());
            return;
        }

        outboundLobbyMessageEmitter.send(OutboundLobbyMessage.forPlayer(kickPlayerRequest.playerId(),
                                                                        new AdminMessage.NoticeInfo(null,
                                                                                                    AdminMessage.Style.KICK)));
    }

    private void closePlayerGame(int playerId, AdminMessage.ClosePlayerGameRequest closePlayerGameRequest) {
        if (userGroupAssignmentRepository.playerLacksPermission(playerId, "ADMIN_KICK_SERVER")) {
            LOG.warnf("Unauthorized kick request with target: %d", closePlayerGameRequest.playerId());
            return;
        }

        outboundLobbyMessageEmitter.send(OutboundLobbyMessage.forPlayer(closePlayerGameRequest.playerId(),
                                                                        new AdminMessage.NoticeInfo(null,
                                                                                                    AdminMessage.Style.KILL)));
    }

    @Incoming("social-inbound")
    public void handleSocialRequest(InboundLobbyMessage<SocialMessage.Client> request) {
        int playerId = request.playerId();
        switch (request.message()) {
            case SocialMessage.ListAvatarsRequest(String requestId) -> {
                Set<AvatarInfo> avatars = assignedAvatarRepository.findAssignedAvatarsByPlayer(playerId).stream().map(
                        AvatarInfo::from).collect(Collectors.toSet());
                SocialMessage.AvatarInfoList avatarInfoList = new SocialMessage.AvatarInfoList(requestId, avatars);
                outboundLobbyMessageEmitter.send(OutboundLobbyMessage.forSession(request.sessionId(), avatarInfoList));
            }
            case SocialMessage.SelectAvatarRequest(_, String avatarUrl) -> {
                Avatar avatar = assignedAvatarRepository.updateSelectedAvatar(playerId, avatarUrl);
                playerStateTracker.updatePlayer(playerId, player -> player.setAvatar(avatar));
            }
            case SocialMessage.RemoveAvatarRequest _ -> removeAvatar(playerId);
            case SocialMessage.SocialAddRequest(_, Integer friendId, Integer foeId) -> {
                if (friendId != null) {
                    addSocialRelationship(playerId, friendId, FriendOrFoeEntity.Status.FRIEND);
                }
                if (foeId != null) {
                    addSocialRelationship(playerId, foeId, FriendOrFoeEntity.Status.FOE);
                }
            }
            case SocialMessage.SocialRemoveRequest(_, Integer friendId, Integer foeId) -> {
                if (friendId != null) {
                    removeSocialRelationship(playerId, friendId);
                }
                if (foeId != null) {
                    removeSocialRelationship(playerId, foeId);
                }
            }
        }
    }

    private void addSocialRelationship(int playerId, int targetId, FriendOrFoeEntity.Status status) {
        friendOrFoeRepository.upsertPlayerRelationship(playerId, targetId, status);
    }

    private void removeSocialRelationship(int playerId, int targetId) {
        friendOrFoeRepository.deletePlayerRelationship(playerId, targetId);
    }

    private void removeAvatar(int playerId) {
        assignedAvatarRepository.removeSelectedAvatar(playerId);
        playerStateTracker.updatePlayer(playerId, Player::clearAvatar);
    }
}
