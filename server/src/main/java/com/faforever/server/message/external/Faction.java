package com.faforever.server.message.external;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
public enum Faction {
    UEF("uef", 1),

    AEON("aeon", 2),

    CYBRAN("cybran", 3),

    SERAPHIM("seraphim", 4),

    RANDOM("random", 5),

    CIVILIAN("civilian", 6);

    private final static Map<String, Faction> FA_STRING_MAP;
    private final static Map<Integer, Faction> FA_INDEX_MAP;

    static {
        Faction[] values = Faction.values();
        Map<String, Faction> stringMap = new HashMap<>(values.length);
        Map<Integer, Faction> indexMap = new HashMap<>(values.length);
        for (Faction value : values) {
            stringMap.put(value.faString(), value);
            indexMap.put(value.faIndex(), value);
        }
        FA_STRING_MAP = Map.copyOf(stringMap);
        FA_INDEX_MAP = Map.copyOf(indexMap);
    }


    private final String faString;
    private final int faIndex;

    @JsonValue
    public String faString() {
        return faString;
    }

    public int faIndex() {
        return faIndex;
    }

    @JsonCreator
    public static @Nullable Faction fromObject(@Nullable Object value) {
        return switch (value) {
            case Integer index -> FA_INDEX_MAP.get(index);
            case String faName -> FA_STRING_MAP.get(faName);
            case null, default -> null;
        };
    }
}
