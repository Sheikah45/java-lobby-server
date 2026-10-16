package com.faforever.server.player;

import com.faforever.server.domain.FriendOrFoeEntity;
import com.faforever.server.domain.PlayerEntity;
import com.faforever.server.game.Game;
import com.faforever.server.rating.Leaderboard;
import com.faforever.server.rating.LeaderboardRating;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class Player {

    @Getter
    private final int id;
    @Getter
    private final String username;

    private @Nullable Game game;

    private final Map<Leaderboard, LeaderboardRating> leaderboardRatings = new ConcurrentHashMap<>();
    private final Set<Long> sessionIds = ConcurrentHashMap.newKeySet();

    private Set<Integer> friendIds = Set.of();
    private Set<Integer> foeIds = Set.of();

    public Player(int id, String username) {
        this.id = id;
        this.username = username;
    }

    static Player from(PlayerEntity playerEntity) {
        Player player = new Player(playerEntity.getId(), playerEntity.getName());
        Collection<LeaderboardRating> leaderboardRatings = playerEntity.getLeaderboardRatings()
                                                                       .stream()
                                                                       .map(leaderboardRatingEntity -> {
                                                                           Leaderboard leaderboard = new Leaderboard(
                                                                                   leaderboardRatingEntity.getLeaderboard()
                                                                                                          .getTechnicalName());
                                                                           return new LeaderboardRating(leaderboard,
                                                                                   leaderboardRatingEntity.getTotalGames(),
                                                                                   leaderboardRatingEntity.getMean(),
                                                                                   leaderboardRatingEntity.getDeviation());
                                                                       })
                                                                       .collect(Collectors.toSet());
        player.updateLeaderboardRatings(leaderboardRatings);

        Map<FriendOrFoeEntity.Status, Set<Integer>> friendOrForMap = playerEntity.getFriendOrFoes()
                                                                                 .stream()
                                                                                 .collect(Collectors.groupingBy(
                                                                                         FriendOrFoeEntity::getStatus,
                                                                                         Collectors.mapping(
                                                                                                 friendOrFoe -> friendOrFoe.getId()
                                                                                                                           .subjectId(),
                                                                                                 Collectors.toSet())));

        player.setFriends(friendOrForMap.getOrDefault(FriendOrFoeEntity.Status.FRIEND, Set.of()));
        player.setFoes(friendOrForMap.getOrDefault(FriendOrFoeEntity.Status.FOE, Set.of()));

        return player;
    }

    public void setFriends(Collection<Integer> playerIds) {
        friendIds = Set.copyOf(playerIds);
    }

    public boolean isFriend(int playerId) {
        return friendIds.contains(playerId);
    }

    public void setFoes(Collection<Integer> playerIds) {
        foeIds = Set.copyOf(playerIds);
    }

    public boolean isFoe(int playerId) {
        return foeIds.contains(playerId);
    }

    public Optional<LeaderboardRating> getRating(Leaderboard leaderboard) {
        return Optional.ofNullable(leaderboardRatings.get(leaderboard));
    }

    public void updateLeaderboardRatings(Collection<LeaderboardRating> leaderboardRatings) {
        for (LeaderboardRating leaderboardRating : leaderboardRatings) {
            this.leaderboardRatings.put(leaderboardRating.leaderboard(), leaderboardRating);
        }
    }

    public Map<Leaderboard, LeaderboardRating> getLeaderboardRatings() {
        return Map.copyOf(leaderboardRatings);
    }

    public void setGame(Game game) {
        if (Objects.equals(game, this.game)) {
            return;
        }
        if (this.game != null) {
            throw new IllegalStateException("Player is already associated with game");
        }
        this.game = game;
    }

    public void clearGame() {
        if (game == null) {
            return;
        }
        this.game = null;
    }

    public Optional<Game> getGame() {
        return Optional.ofNullable(game);
    }

    void addSession(long sessionId) {
        sessionIds.add(sessionId);
    }

    void removeSession(long sessionId) {
        sessionIds.remove(sessionId);
    }

    boolean hasNoSession() {
        return sessionIds.isEmpty();
    }
}
