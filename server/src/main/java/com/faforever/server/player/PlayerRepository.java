package com.faforever.server.player;

import com.faforever.server.domain.PlayerEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class PlayerRepository implements PanacheRepositoryBase<PlayerEntity, Integer> {

    @Transactional
    public boolean playerHasPermission(int playerId, String permission) {
        return count("player.id = ?1 and userGroups.groupPermissions.technicalName = ?1", playerId, permission) > 0;
    }

}
