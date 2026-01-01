package com.faforever.server.social;

import com.faforever.server.domain.AssignedAvatarEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.jspecify.annotations.Nullable;

import java.util.List;

@ApplicationScoped
class AssignedAvatarRepository implements PanacheRepositoryBase<AssignedAvatarEntity, Integer> {

    public List<AssignedAvatarEntity> findAssignedAvatarsByPlayer(int playerId) {
        return find("where player.id = ?1 and (expirationDate is null or expirationDate < current_timestamp)", playerId).list();
    }

    public void updateSelectedAvatar(int playerId, @Nullable String avatarUrl) {
        update("set selected = false where player.id = ?1", playerId);
        if (avatarUrl != null) {
            update("set selected = true where player.id = ?1 and avatar.url = ?2", playerId, avatarUrl);
        }
    }

}
