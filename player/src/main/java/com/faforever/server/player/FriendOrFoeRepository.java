package com.faforever.server.player;

import com.faforever.server.domain.FriendOrFoeEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
class FriendOrFoeRepository implements PanacheRepositoryBase<FriendOrFoeEntity, FriendOrFoeEntity.Id> {

    @Transactional
    void upsertPlayerRelationship(int playerId, int subjectId, FriendOrFoeEntity.Status status) {
        getEntityManager().merge(new FriendOrFoeEntity(new FriendOrFoeEntity.Id(playerId, subjectId), status));
    }

    @Transactional
    void deletePlayerRelationship(int playerId, int subjectId) {
        FriendOrFoeEntity.Id id = new FriendOrFoeEntity.Id(playerId, subjectId);
        deleteById(id);
    }

}
