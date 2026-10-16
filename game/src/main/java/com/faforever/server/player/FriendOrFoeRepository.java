package com.faforever.server.player;

import com.faforever.server.domain.FriendOrFoeEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class FriendOrFoeRepository implements PanacheRepositoryBase<FriendOrFoeEntity, FriendOrFoeEntity.Id> {

    @Transactional
    public Map<Integer, Map<FriendOrFoeEntity.Status, Set<Integer>>> loadPlayerFriendOrFoes(Collection<Integer> playerIds) {
        return find("playerId in ?1", playerIds).stream()
                                                .collect(Collectors.groupingBy(
                                                        friendOrFoeEntity -> friendOrFoeEntity.getId().playerId(),
                                                        Collectors.groupingBy(FriendOrFoeEntity::getStatus,
                                                                Collectors.mapping(
                                                                        friendOrFoeEntity -> friendOrFoeEntity.getId()
                                                                                                              .subjectId(),
                                                                        Collectors.toSet()))));
    }

}
