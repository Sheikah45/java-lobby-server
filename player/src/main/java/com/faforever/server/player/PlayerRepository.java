package com.faforever.server.player;

import com.faforever.server.domain.PlayerEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.jspecify.annotations.Nullable;

@ApplicationScoped
class PlayerRepository implements PanacheRepositoryBase<PlayerEntity, Integer> {

    @Transactional
    @Nullable Player loadPlayer(int playerId) {
        return findByIdOptional(playerId).map(Player::from).orElse(null);
    }

}
