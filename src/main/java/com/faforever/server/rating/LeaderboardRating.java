package com.faforever.server.rating;

public record LeaderboardRating(
        Leaderboard leaderboard,
        int totalGames,
        double mean,
        double deviation
) {

    public double displayedRating() {
        return mean - 3 * deviation;
    }
}
