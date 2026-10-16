package com.faforever.server.player;

import com.faforever.server.domain.PlayerEntity;
import com.faforever.server.rating.Leaderboard;
import com.faforever.server.rating.LeaderboardRating;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
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
    @Setter(AccessLevel.PACKAGE)
    private String username;

    @Getter
    @Setter(AccessLevel.PACKAGE)
    private String country = "";
    private @Nullable String clan;
    private @Nullable Avatar avatar;

    private final Map<Leaderboard, LeaderboardRating> leaderboardRatings = new ConcurrentHashMap<>();
    private final Set<Integer> friendIds = ConcurrentHashMap.newKeySet();
    private final Set<Integer> foeIds = ConcurrentHashMap.newKeySet();
    private final Set<Long> sessionIds = ConcurrentHashMap.newKeySet();

    public Player(int id, String username) {
        this.id = id;
        this.username = username;
    }

    static Player from(PlayerEntity playerEntity) {
        Player player = new Player(playerEntity.getId(), playerEntity.getName());
        if (playerEntity.getSelectedAvatar() != null) {
            player.setAvatar(Avatar.from(playerEntity.getSelectedAvatar()));
        }
        if (playerEntity.getClan() != null) {
            player.setClan(playerEntity.getClan().getTag());
        }
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
        playerEntity.getFriendOrFoes().forEach(friendOrFoe -> {
            switch (friendOrFoe.getStatus()) {
                case FOE -> player.addFoe(friendOrFoe.getId().subjectId());
                case FRIEND -> player.addFriend(friendOrFoe.getId().subjectId());
            }
        });
        return player;
    }

    public State getState() {
        return State.ONLINE;
    }

    void addFriend(int playerId) {
        friendIds.add(playerId);
        foeIds.remove(playerId);
    }

    public boolean isFriend(int playerId) {
        return friendIds.contains(playerId);
    }

    Set<Integer> getFriendIds() {
        return Set.copyOf(friendIds);
    }

    void addFoe(int playerId) {
        foeIds.add(playerId);
        friendIds.remove(playerId);
    }

    public boolean isFoe(int playerId) {
        return foeIds.contains(playerId);
    }

    void removeFriendOrFoe(int playerId) {
        foeIds.remove(playerId);
        friendIds.remove(playerId);
    }

    Set<Integer> getFoeIds() {
        return Set.copyOf(foeIds);
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

    void setClan(String clan) {
        if (Objects.equals(clan, this.clan)) {
            return;
        }

        this.clan = clan;
    }

    void clearClan() {
        if (this.clan == null) {
            return;
        }

        this.clan = null;
    }

    public Optional<String> getClan() {
        return Optional.ofNullable(clan);
    }

    void setAvatar(Avatar avatar) {
        if (Objects.equals(avatar, this.avatar)) {
            return;
        }

        this.avatar = avatar;
    }

    void clearAvatar() {
        if (this.avatar == null) {
            return;
        }

        this.avatar = null;
    }

    public Optional<Avatar> getAvatar() {
        return Optional.ofNullable(avatar);
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

    public enum State {
        ONLINE, OFFLINE
    }

}
