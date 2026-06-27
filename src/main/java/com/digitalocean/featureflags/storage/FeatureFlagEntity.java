package com.digitalocean.featureflags.storage;

import com.digitalocean.featureflags.domain.Variation;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "feature_flag")
public class FeatureFlagEntity {

    @Id
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_state", nullable = false)
    private Variation defaultState;

    @Column(name = "rollout_percentage")
    private Integer rolloutPercentage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "flag", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RuleEntity> rules = new ArrayList<>();

    protected FeatureFlagEntity() {
    }

    public FeatureFlagEntity(String name, Variation defaultState, Integer rolloutPercentage) {
        this.name = name;
        this.defaultState = defaultState;
        this.rolloutPercentage = rolloutPercentage;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public void replaceRules(List<RuleEntity> newRules) {
        this.rules.clear();
        for (RuleEntity rule : newRules) {
            rule.setFlag(this);
            this.rules.add(rule);
        }
    }

    public String getName() {
        return name;
    }

    public Variation getDefaultState() {
        return defaultState;
    }

    public void setDefaultState(Variation defaultState) {
        this.defaultState = defaultState;
    }

    public Integer getRolloutPercentage() {
        return rolloutPercentage;
    }

    public void setRolloutPercentage(Integer rolloutPercentage) {
        this.rolloutPercentage = rolloutPercentage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<RuleEntity> getRules() {
        return rules;
    }
}
