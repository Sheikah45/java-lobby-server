package com.faforever.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "game_stats")
public class GameEntity extends AbstractEntity {

    @Column(name = "gameName")
    private String title;

    @Column(name = "startTime")
    private OffsetDateTime startDateTime;

    @Column(name = "endTime")
    private OffsetDateTime endDateTime;

    @ManyToOne
    @JoinColumn(name = "gameMod")
    private FeaturedModEntity featuredMod;

    @ManyToOne
    @JoinColumn(name = "host")
    private PlayerEntity host;

    @ManyToOne
    @JoinColumn(name = "mapId")
    private MapVersionEntity mapVersion;

}
