package com.faforever.server.mapstruct;


import com.faforever.server.config.DefaultMapperConfig;
import org.jspecify.annotations.Nullable;
import org.mapstruct.Condition;
import org.mapstruct.Mapper;

import java.util.Optional;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
@Mapper(config = DefaultMapperConfig.class)
public abstract class OptionalMapper {

    public <T> @Nullable T map(Optional<T> optional) {
        return optional.orElseThrow();
    }

    @Condition
    public <T> boolean isPresent(Optional<T> optional) {
        return optional.isPresent();
    }

}
