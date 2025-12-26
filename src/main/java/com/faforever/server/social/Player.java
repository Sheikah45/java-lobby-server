package com.faforever.server.social;

import com.faforever.server.message.dto.PlayerInfo;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Player {

    @Getter
    private final int id;
    @Getter
    private String username;
    private State state = State.IDLE;

    private @Nullable String country;
    private @Nullable String clan;
    private @Nullable Avatar avatar;
    private Map<String, Integer> gameCount = new HashMap<>();
    private Set<Integer> friendIds = new HashSet<>();
    private Set<Integer> foeIds = new HashSet<>();

    public Player(int id, String username) {
        this.id = id;
        this.username = username;
    }


    public PlayerInfo asPlayerInfo() {
        return new PlayerInfo(id, username);
    }

}
