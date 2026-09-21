package com.faforever.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLJoinTableRestriction;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "login")
public class PlayerEntity extends AbstractEntity {

    @Column(name = "login")
    private String name;

    @ManyToOne
    @JoinTable(
            name = "clan_membership",
            joinColumns = @JoinColumn(name = "player_id"),
            inverseJoinColumns = @JoinColumn(name = "clan_id")
    )
    private ClanEntity clan;

    @ManyToOne
    @JoinTable(
            name = "avatars",
            joinColumns = @JoinColumn(name = "idUser"),
            inverseJoinColumns = @JoinColumn(name = "idAvatar")
    )
    @SQLJoinTableRestriction("expires_at is null or expires_at < current_timestamp and selected = true")
    private AvatarEntity selectedAvatar;

    @OneToMany
    @JoinColumn(name = "user_id")
    private Set<FriendOrFoeEntity> friendOrFoes = new HashSet<>();

    @OneToMany(mappedBy = "player")
    private Set<LeaderboardRatingEntity> leaderboardRatings = new HashSet<>();
}
