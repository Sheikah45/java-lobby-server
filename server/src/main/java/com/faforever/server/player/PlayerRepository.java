package com.faforever.server.player;

import com.faforever.server.domain.PlayerEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
class PlayerRepository implements PanacheRepositoryBase<PlayerEntity, Integer> {

    @Transactional
    public boolean playerLacksPermission(int playerId, String permission) {
        return count("player.id = ?1 and userGroups.groupPermissions.technicalName = ?1", playerId, permission) == 0;
    }

    @Transactional
    public Player loadPlayer(int playerId) {
        return Player.from(findByIdOptional(playerId).orElseThrow());
    }

}
