package com.faforever.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "game_featuredMods")
public class FeaturedModEntity extends AbstractEntity {

    @Column(name = "gamemod")
    private String technicalName;

}
