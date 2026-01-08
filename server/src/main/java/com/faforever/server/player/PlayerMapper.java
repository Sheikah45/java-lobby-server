package com.faforever.server.player;

import com.faforever.server.config.DefaultMapperConfig;
import com.faforever.server.domain.FriendOrFoeEntity;
import com.faforever.server.domain.PlayerEntity;
import jakarta.transaction.Transactional;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = DefaultMapperConfig.class)
abstract class PlayerMapper {

    @Transactional(Transactional.TxType.MANDATORY)
    @Mapping(target = "username", source = "player.name")
    @Mapping(target = "avatar", source = "selectedAvatar")
    @Mapping(target = "foeIds", ignore = true)
    @Mapping(target = "friendIds", ignore = true)
    @Mapping(target = "game", ignore = true)
    @Mapping(target = "clan", source = "clan.tag")
    @Mapping(target = "id", source = "id")
    abstract Player map(PlayerEntity player);

    @AfterMapping
    protected void mapFriendsAndFoes(PlayerEntity playerEntity, @MappingTarget Player player) {
        for (FriendOrFoeEntity friendOrFoe : playerEntity.getFriendsAndFoes()) {
            switch (friendOrFoe.getStatus()) {
                case FOE -> player.addFoe(friendOrFoe.getId().subjectId());
                case FRIEND -> player.addFriend(friendOrFoe.getId().subjectId());
            }
        }
    }
}
