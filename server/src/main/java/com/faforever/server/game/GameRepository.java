package com.faforever.server.game;

import com.faforever.server.domain.GameEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class GameRepository implements PanacheRepositoryBase<GameEntity, Integer> {

    public int findMaxGameId() {
        return find("select max(g.id) from GameEntity g").project(Integer.class).singleResultOptional().orElse(0);
    }
}
