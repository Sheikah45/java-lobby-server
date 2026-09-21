package com.faforever.server.message.external.dto;

import com.faforever.server.mapstruct.DefaultMapperConfig;
import com.faforever.server.domain.AvatarEntity;
import com.faforever.server.game.Game;
import com.faforever.server.mapstruct.OptionalMapper;
import com.faforever.server.message.external.GameMessage;
import com.faforever.server.player.Player;
import com.faforever.server.rating.Leaderboard;
import com.faforever.server.rating.LeaderboardRating;
import com.faforever.server.player.Avatar;
import com.faforever.server.player.State;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Collection;
import java.util.List;
import java.util.Set;

@Mapper(config = DefaultMapperConfig.class, uses = OptionalMapper.class)
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

    public abstract AvatarInfo map(Avatar avatar);

    public abstract List<AvatarInfo> mapAvatars(Collection<Avatar> avatars);

    @Mapping(target = "ratingMax", source = "ratingRange.max")
    @Mapping(target = "ratingMin", source = "ratingRange.min")
    @Mapping(target = "uid", source = "id")
    @Mapping(target = "host", source = "host.username")
    @Mapping(target = "state", ignore = true)
    @Mapping(target = "simMods", ignore = true)
    @Mapping(target = "passwordProtected", ignore = true)
    @Mapping(target = "numberOfPlayers", ignore = true)
    @Mapping(target = "maxPlayers", ignore = true)
    @Mapping(target = "mapFilePath", ignore = true)
    @Mapping(target = "leaderboard", ignore = true)
    @Mapping(target = "launchedAt", ignore = true)
    @Mapping(target = "hostedAt", ignore = true)
    public abstract GameInfo map(Game game);

    public abstract List<GameInfo> mapGames(Collection<Game> games);

    @Mapping(target = "team", ignore = true)
    @Mapping(target = "name", source = "title")
    @Mapping(target = "mapPosition", ignore = true)
    @Mapping(target = "mapName", ignore = true)
    @Mapping(target = "lobbyMode", source = "gameType")
    @Mapping(target = "leaderboard", source = "leaderboard.technicalName")
    @Mapping(target = "gameOptions", ignore = true)
    @Mapping(target = "gameId", source = "id")
    @Mapping(target = "faction", ignore = true)
    @Mapping(target = "expectedPlayers", ignore = true)
    public abstract GameMessage.GameLaunchResponse mapToGameLaunch(Game game);

    LobbyMode map(GameType gameType) {
        return switch (gameType) {
            case COOP, CUSTOM, TUTORIAL -> LobbyMode.DEFAULT_LOBBY;
            case MATCHMAKER, TOURNAMENT -> LobbyMode.AUTO_LOBBY;
        };
    }
}
