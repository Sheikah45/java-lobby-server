package com.faforever.server.player;

import com.faforever.server.connection.SessionController;
import com.faforever.server.game.Game;
import com.faforever.server.rating.Leaderboard;
import com.faforever.server.rating.LeaderboardRating;
import com.faforever.server.social.Avatar;
import com.faforever.server.social.State;
import jakarta.enterprise.context.Dependent;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Dependent
@RequiredArgsConstructor
public class Player {

    private final PlayerService playerService;

    @Getter
    @Setter(AccessLevel.PACKAGE)
    private Details details;

    @Getter
    @Setter(AccessLevel.PACKAGE)
    private String country = "";
    private @Nullable Avatar avatar;
    private @Nullable Game game;

    private final Map<Leaderboard, LeaderboardRating> leaderboardRatings = new ConcurrentHashMap<>();
    private final Set<Integer> friendIds = ConcurrentHashMap.newKeySet();
    private final Set<Integer> foeIds = ConcurrentHashMap.newKeySet();
    private final Set<SessionController> sessions = ConcurrentHashMap.newKeySet();

    public State getState() {
        return State.IDLE;
    }

    public void kick() {
        sessions.forEach(SessionController::kick);
    }

    public void closeGame() {
        sessions.forEach(SessionController::kick);
    }

    public void addSession(SessionController session) {
        boolean firstConnection = isNotConnected();
        sessions.add(session);
        if (firstConnection) {
            playerService.markDirty(this);
        }
    }

    public void removeSession(SessionController session) {
        sessions.remove(session);
        if (isNotConnected()) {
            playerService.markDisconnected(this);
        }
    }

    boolean isNotConnected() {
        return sessions.isEmpty();
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

    public void setLeaderboardRatings(Collection<LeaderboardRating> leaderboardRatings) {
        for (LeaderboardRating leaderboardRating : leaderboardRatings) {
            this.leaderboardRatings.put(leaderboardRating.leaderboard(), leaderboardRating);
        }
    }

    public Map<Leaderboard, LeaderboardRating> getLeaderboardRatings() {
        return Map.copyOf(leaderboardRatings);
    }

    public void setAvatar(@Nullable Avatar avatar) {
        if (avatar == this.avatar) {
            return;
        }

        this.avatar = avatar;
        playerService.markDirty(this);
    }

    public void setGame(Game game) {
        if (game == this.game) {
            return;
        }
        if (this.game != null) {
            throw new IllegalStateException("Player is already associated with game");
        }
        this.game = game;
        playerService.markDirty(this);
    }

    public void clearGame() {
        if (game == null) {
            return;
        }
        this.game = null;
        playerService.markDirty(this);
    }

    public Optional<Game> getGame() {
        return Optional.ofNullable(game);
    }

    public Optional<Avatar> getAvatar() {
        return Optional.ofNullable(avatar);
    }

    public record Details(
            int id,
            String username,
            @Nullable String clan
    ) {}
}
