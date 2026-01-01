package com.faforever.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "friends_and_foes")
public class FriendOrFoeEntity {

    @EmbeddedId
    private Id id;

    @MapsId("playerId")
    @ManyToOne
    @JoinColumn(name = "user_id")
    private PlayerEntity player;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private Status status;

    public enum Status {
        FRIEND, FOE
    }

    @Embeddable
    public record Id(
            @Column(name = "user_id") int playerId,
            @Column(name = "subject_id") int subjectId
    ) {}

}
