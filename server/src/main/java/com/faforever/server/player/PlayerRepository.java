package com.faforever.server.player;

import com.faforever.server.domain.PlayerEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
class PlayerRepository implements PanacheRepositoryBase<PlayerEntity, Integer> {

    @Transactional
    Player loadPlayer(int playerId) {
        return Player.from(findByIdOptional(playerId).orElseThrow());
    }

}
