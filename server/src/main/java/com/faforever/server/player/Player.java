package com.faforever.server.player;

import com.faforever.server.connection.SessionController;
import com.faforever.server.game.Game;
import com.faforever.server.message.AdminMessage;
import com.faforever.server.rating.Leaderboard;
import com.faforever.server.rating.LeaderboardRating;
import com.faforever.server.social.Avatar;
import com.faforever.server.social.State;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Player {

    @Getter
    private final int id;
    @Getter
    @Setter(AccessLevel.PACKAGE)
    private String username;
    @Getter
    @Setter(AccessLevel.PACKAGE)
    private String country = "";
    @Setter(AccessLevel.PACKAGE)
    private @Nullable String clan;
    @Getter
    @Setter(AccessLevel.PACKAGE)
    private @Nullable Avatar avatar;
    private @Nullable Game game;

    private final Map<Leaderboard, LeaderboardRating> leaderboardRatings = new ConcurrentHashMap<>();
    private final Set<Integer> friendIds = ConcurrentHashMap.newKeySet();
    private final Set<Integer> foeIds = ConcurrentHashMap.newKeySet();
    private final Set<SessionController> sessions = ConcurrentHashMap.newKeySet();

    Player(int id, String username, @Nullable String clan, @Nullable Avatar avatar, Collection<LeaderboardRating> leaderboardRatings) {
        this.id = id;
        this.username = username;
        this.clan = clan;
        this.avatar = avatar;
        for (LeaderboardRating leaderboardRating : leaderboardRatings) {
            this.leaderboardRatings.put(leaderboardRating.leaderboard(), leaderboardRating);
        }
    }

    public State getState() {
        return State.IDLE;
    }

    public void kick() {
        sessions.forEach(session -> {
            session.broadcast(new AdminMessage.NoticeInfo(null, AdminMessage.Style.KICK));
            session.close();
        });
    }

    public void addSession(SessionController session) {
        sessions.add(session);
    }

    public void removeSession(SessionController session) {
        sessions.remove(session);
    }

    public boolean isConnected() {
        return !sessions.isEmpty();
    }

    public void addFriend(int playerId) {
        friendIds.add(playerId);
    }

    public void removeFriend(int playerId) {
        friendIds.remove(playerId);
    }

    public boolean isFriend(int playerId) {
        return friendIds.contains(playerId);
    }

    public Set<Integer> getFriendIds() {
        return Set.copyOf(friendIds);
    }

    public void addFoe(int playerId) {
        foeIds.add(playerId);
    }

    public void removeFoe(int playerId) {
        foeIds.remove(playerId);
    }

    public boolean isFoe(int playerId) {
        return foeIds.contains(playerId);
    }

    public Set<Integer> getFoeIds() {
        return Set.copyOf(foeIds);
    }

    public Optional<LeaderboardRating> getRating(Leaderboard leaderboard) {
        return Optional.ofNullable(leaderboardRatings.get(leaderboard));
    }

    public Map<Leaderboard, LeaderboardRating> getLeaderboardRatings() {
        return Map.copyOf(leaderboardRatings);
    }

    public Optional<String> getClan() {
        return Optional.ofNullable(clan);
    }

    public void setGame(Game game) {
        if (this.game != null) {
            throw new IllegalStateException("Player is already associated with game");
        }
        this.game = game;
    }

    public void clearGame() {
        this.game = null;
    }

    public Optional<Game> getGame() {
        return Optional.ofNullable(game);
    }
}
