package com.faforever.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "map_version")
public class MapVersionEntity extends AbstractEntity {

    @Column(name = "filename")
    private String mapFilename;

}
