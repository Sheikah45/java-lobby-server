package com.faforever.server.rating;

import lombok.Getter;

public class LeaderboardRating {
    @Getter
    private final Leaderboard leaderboard;
    @Getter
    private final int totalGames;
    @Getter
    private final double mean;
    @Getter
    private final double deviation;

    public LeaderboardRating(Leaderboard leaderboard, int totalGames, double mean, double deviation) {
        this.leaderboard = leaderboard;
        this.totalGames = totalGames;
        this.mean = mean;
        this.deviation = deviation;
    }

    double getDisplayedRating() {
        return mean - 3 * deviation;
    }
}
