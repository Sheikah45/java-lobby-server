package com.faforever.server.social;

import com.faforever.server.domain.AssignedAvatarEntity;
import com.faforever.server.player.Player;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.jspecify.annotations.Nullable;

import java.util.List;

@ApplicationScoped
class AssignedAvatarRepository implements PanacheRepositoryBase<AssignedAvatarEntity, Integer> {

    public List<AssignedAvatarEntity> findAssignedAvatarsByPlayer(Player player) {
        return list("where player.id = ?1 and (expirationDate is null or expirationDate < current_timestamp)", player.getId());
    }

    public void updateSelectedAvatar(Player player, @Nullable String avatarUrl) {
        update("set selected = false where player.id = ?1", player.getId());
        if (avatarUrl != null) {
            update("set selected = true where player.id = ?1 and avatar.url = ?2", player.getId(), avatarUrl);
        }
    }

}
