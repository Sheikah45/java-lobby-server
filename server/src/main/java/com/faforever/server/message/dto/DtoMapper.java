package com.faforever.server.message.dto;

import com.faforever.server.config.DefaultMapperConfig;
import com.faforever.server.domain.AvatarEntity;
import com.faforever.server.rating.Leaderboard;
import com.faforever.server.rating.LeaderboardRating;
import com.faforever.server.social.Player;
import com.faforever.server.social.State;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Collection;
import java.util.List;
import java.util.Set;

@Mapper(config = DefaultMapperConfig.class)
public abstract class DtoMapper {

    public abstract Set<PlayerInfo> map(Collection<Player> players);

    @Mapping(target = "state", source = "state")
    @Mapping(target = "ratings", source = "leaderboardRatings")
    @Mapping(target = "login", source = "username")
    public abstract PlayerInfo map(Player player);

    public PlayerInfo.State map(State state) {
        return switch (state) {
            case OFFLINE -> PlayerInfo.State.OFFLINE;
            case null, default -> null;
        };
    }

    public String map(Leaderboard leaderboard) {
        return leaderboard.technicalName();
    }

    @Mapping(target = "rating.deviation", source = "deviation")
    @Mapping(target = "rating.mean", source = "mean")
    @Mapping(target = "numberOfGames", source = "totalGames")
    public abstract LeaderboardStats map(LeaderboardRating leaderboardRating);

    public abstract AvatarInfo map(AvatarEntity avatar);

    public abstract List<AvatarInfo> mapAvatars(Collection<AvatarEntity> avatars);

}
