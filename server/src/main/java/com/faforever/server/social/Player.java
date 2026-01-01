package com.faforever.server.social;

import com.faforever.server.connection.SessionController;
import com.faforever.server.message.AdminMessage;
import com.faforever.server.rating.Leaderboard;
import com.faforever.server.rating.LeaderboardRating;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
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
    @Getter
    @Setter(AccessLevel.PACKAGE)
    private @Nullable String clan;
    @Getter
    @Setter(AccessLevel.PACKAGE)
    private @Nullable Avatar avatar;

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
            this.leaderboardRatings.put(leaderboardRating.getLeaderboard(), leaderboardRating);
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

    void addFriend(int playerId) {
        friendIds.add(playerId);
    }

    void removeFriend(int playerId) {
        friendIds.remove(playerId);
    }

    public boolean isFriend(int playerId) {
        return friendIds.contains(playerId);
    }

    public Set<Integer> getFriendIds() {
        return Set.copyOf(friendIds);
    }

    void addFoe(int playerId) {
        foeIds.add(playerId);
    }

    void removeFoe(int playerId) {
        foeIds.remove(playerId);
    }

    public boolean isFoe(int playerId) {
        return foeIds.contains(playerId);
    }

    public Set<Integer> getFoeIds() {
        return Set.copyOf(foeIds);
    }

    public @Nullable LeaderboardRating getRating(Leaderboard leaderboard) {
        return leaderboardRatings.get(leaderboard);
    }

    public Map<Leaderboard, LeaderboardRating> getLeaderboardRatings() {
        return Map.copyOf(leaderboardRatings);
    }
}
