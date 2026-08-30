package com.faforever.server.social;

import com.faforever.server.domain.AssignedAvatarEntity;
import com.faforever.server.domain.FriendOrFoeEntity;
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

    private final FriendOrFoeRepository friendOrFoeRepository;
    private final AssignedAvatarRepository assignedAvatarRepository;

    @Transactional
    public void changeSocialRelationship(SocialRequest.FriendOrFoe friendOrFoeRequest) {
        int playerId = friendOrFoeRequest.playerId();
        int targetId = friendOrFoeRequest.targetId();
        switch (friendOrFoeRequest) {
            case SocialRequest.FriendOrFoe.AddFriend _ ->
                    friendOrFoeRepository.upsertPlayerRelationship(playerId, targetId, FriendOrFoeEntity.Status.FRIEND);
            case SocialRequest.FriendOrFoe.AddFoe _ ->
                    friendOrFoeRepository.upsertPlayerRelationship(playerId, targetId, FriendOrFoeEntity.Status.FOE);
            case SocialRequest.FriendOrFoe.RemoveFriend _, SocialRequest.FriendOrFoe.RemoveFoe _ ->
                    friendOrFoeRepository.deletePlayerRelationship(playerId, targetId);
        }
    }

    @Transactional
    public Set<Avatar> getAvatars(SocialRequest.Avatars avatarsRequest) {
        return assignedAvatarRepository.findAssignedAvatarsByPlayer(avatarsRequest.playerId())
                                       .stream()
                                       .map(AssignedAvatarEntity::getAvatar)
                                       .map(avatarEntity -> new Avatar(avatarEntity.getUrl(),
                                                                      avatarEntity.getDescription()))
                                       .collect(Collectors.toSet());
    }

    @Transactional
    public void selectAvatar(SocialRequest.SelectAvatar selectRequest) {
        assignedAvatarRepository.updateSelectedAvatar(selectRequest.playerId(), selectRequest.avatarUrl());
    }

}
