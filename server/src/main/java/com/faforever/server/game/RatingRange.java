package com.faforever.server.game;

import org.jspecify.annotations.Nullable;

public record RatingRange(@Nullable Integer min, @Nullable Integer max) {

    boolean inRange(double rating) {
        return (min == null || rating >= min) && (max == null || rating <= max);
    }

}
