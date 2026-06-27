package com.digitalocean.featureflags.storage;

import com.digitalocean.featureflags.domain.Variation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "flag_override")
@IdClass(FlagOverrideId.class)
public class FlagOverrideEntity {

    @Id
    @Column(name = "flag_name")
    private String flagName;

    @Id
    @Column(name = "user_id")
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Variation state;

    protected FlagOverrideEntity() {
    }

    public FlagOverrideEntity(String flagName, String userId, Variation state) {
        this.flagName = flagName;
        this.userId = userId;
        this.state = state;
    }

    public String getFlagName() {
        return flagName;
    }

    public String getUserId() {
        return userId;
    }

    public Variation getState() {
        return state;
    }

    public void setState(Variation state) {
        this.state = state;
    }
}
