package com.faforever.server.message.client.dto;

import com.faforever.server.player.Player;
import com.faforever.server.rating.LeaderboardRating;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.stream.Collectors;

public record PlayerInfo(
        int id,
        String login,
        @Nullable String clan,
        @Nullable AvatarInfo avatar,
        String country,
        Map<String, LeaderboardStats> ratings,
        @Nullable State state
) {

    public enum State {
        @JsonProperty("offline") OFFLINE
    }

    public static PlayerInfo from(Player player) {
        Map<String, LeaderboardStats> leaderboardStatsMap = player.getLeaderboardRatings()
                                                                  .entrySet()
                                                                  .stream()
                                                                  .map(entry -> {
                                                                      LeaderboardRating leaderboardRating = entry.getValue();
                                                                      LeaderboardStats.Rating rating = new LeaderboardStats.Rating(
                                                                              leaderboardRating.mean(),
                                                                              leaderboardRating.deviation());
                                                                      LeaderboardStats leaderboardStats = new LeaderboardStats(
                                                                              leaderboardRating.totalGames(), rating);
                                                                      return Map.entry(entry.getKey().technicalName(),
                                                                              leaderboardStats);
                                                                  })
                                                                  .collect(Collectors.toMap(Map.Entry::getKey,
                                                                          Map.Entry::getValue));
        State state = switch (player.getState()) {
            case ONLINE -> null;
            case OFFLINE -> State.OFFLINE;
        };
        String clan = player.getClan().orElse(null);
        AvatarInfo avatar = player.getAvatar().map(AvatarInfo::from).orElse(null);
        return new PlayerInfo(player.getId(), player.getUsername(), clan, avatar, player.getCountry(),
                leaderboardStatsMap, state);
    }

}
