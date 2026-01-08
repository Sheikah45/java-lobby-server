package com.faforever.server.social;

import com.faforever.server.connection.SessionController;
import com.faforever.server.domain.AssignedAvatarEntity;
import com.faforever.server.domain.FriendOrFoeEntity;
import com.faforever.server.message.SocialMessage;
import com.faforever.server.player.Player;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;

import java.util.Set;
import java.util.stream.Collectors;

@JBossLog
@RequiredArgsConstructor
@ApplicationScoped
public class SocialService {

    private final SessionController sessionController;

    private final FriendOrFoeRepository friendOrFoeRepository;
    private final AssignedAvatarRepository assignedAvatarRepository;

    @Transactional
    public void addSocialRelationship(SocialMessage.SocialAddRequest addRequest) {
        Player player = sessionController.getPlayer();
        Integer friend = addRequest.friendId();
        if (friend != null) {
            friendOrFoeRepository.upsertPlayerRelationship(player.getId(), friend, FriendOrFoeEntity.Status.FRIEND);
            player.addFriend(friend);
        }

        Integer foe = addRequest.foeId();
        if (foe != null) {
            friendOrFoeRepository.upsertPlayerRelationship(player.getId(), foe, FriendOrFoeEntity.Status.FOE);
            player.addFoe(foe);
        }
    }

    @Transactional
    public void removeSocialRelationship(SocialMessage.SocialRemoveRequest removeRequest) {
        Player player = sessionController.getPlayer();
        Integer friend = removeRequest.friendId();
        if (friend != null) {
            friendOrFoeRepository.deletePlayerRelationship(player.getId(), friend);
            player.removeFriend(friend);
        }

        Integer foe = removeRequest.foeId();
        if (foe != null) {
            friendOrFoeRepository.deletePlayerRelationship(player.getId(), foe);
            player.removeFoe(foe);
        }
    }

    @Transactional
    public void sendAvatarList() {
        Set<Avatar> assignedAvatars = assignedAvatarRepository.findAssignedAvatarsByPlayer(
                                                                      sessionController.getPlayer())
                                                              .stream()
                                                              .map(AssignedAvatarEntity::getAvatar)
                                                              .map(avatarEntity -> new Avatar(avatarEntity.getUrl(),
                                                                      avatarEntity.getDescription()))
                                                              .collect(Collectors.toSet());
        sessionController.sendAvatars(assignedAvatars);
    }

    @Transactional
    public void selectAvatar(SocialMessage.SelectAvatarRequest selectRequest) {
        assignedAvatarRepository.updateSelectedAvatar(sessionController.getPlayer(), selectRequest.avatarUrl());
    }

}
