package com.digitalocean.featureflags.storage;

import com.digitalocean.featureflags.domain.Attribute;
import com.digitalocean.featureflags.domain.FlagDefinition;
import com.digitalocean.featureflags.domain.Operator;
import com.digitalocean.featureflags.domain.Rule;
import com.digitalocean.featureflags.domain.Variation;
import com.digitalocean.featureflags.error.FlagNotFoundException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single boundary to persistence. Wraps the JPA repositories, applies the outage guard,
 * and maps between JPA entities and framework-free domain objects so that nothing above this
 * layer touches an entity (no lazy-loading surprises). All read/write methods throw
 * {@link DataAccessException} when {@code simulate-outage} is on.
 */
@Component
public class FlagStore {

    private final FeatureFlagRepository flagRepository;
    private final FlagOverrideRepository overrideRepository;
    private final StorageProperties properties;

    public FlagStore(FeatureFlagRepository flagRepository,
                     FlagOverrideRepository overrideRepository,
                     StorageProperties properties) {
        this.flagRepository = flagRepository;
        this.overrideRepository = overrideRepository;
        this.properties = properties;
    }

    private void guard() {
        if (properties.isSimulateOutage()) {
            throw new DataAccessResourceFailureException("Simulated database outage");
        }
    }

    @Transactional(readOnly = true)
    public boolean exists(String name) {
        guard();
        return flagRepository.existsById(name);
    }

    @Transactional(readOnly = true)
    public Optional<FlagRecord> find(String name) {
        guard();
        return flagRepository.findById(name).map(this::toRecord);
    }

    @Transactional(readOnly = true)
    public List<FlagRecord> findAll() {
        guard();
        return flagRepository.findAll().stream().map(this::toRecord).toList();
    }

    @Transactional(readOnly = true)
    public Optional<FlagDefinition> loadDefinition(String name) {
        guard();
        return flagRepository.findById(name)
                .map(entity -> toDefinition(entity, overrideRepository.findByFlagName(name)));
    }

    @Transactional
    public FlagRecord create(FlagWrite write) {
        guard();
        FeatureFlagEntity entity =
                new FeatureFlagEntity(write.name(), write.defaultState(), write.rolloutPercentage());
        entity.replaceRules(toRuleEntities(write.rules()));
        return toRecord(flagRepository.save(entity));
    }

    @Transactional
    public FlagRecord update(String name, FlagWrite write) {
        guard();
        FeatureFlagEntity entity = flagRepository.findById(name)
                .orElseThrow(() -> new FlagNotFoundException(name));
        entity.setDefaultState(write.defaultState());
        entity.setRolloutPercentage(write.rolloutPercentage());
        entity.replaceRules(toRuleEntities(write.rules()));
        return toRecord(flagRepository.save(entity));
    }

    @Transactional
    public void delete(String name) {
        guard();
        if (!flagRepository.existsById(name)) {
            throw new FlagNotFoundException(name);
        }
        overrideRepository.deleteByFlagName(name);
        flagRepository.deleteById(name);
    }

    @Transactional
    public void putOverride(String flagName, String userId, Variation state) {
        guard();
        overrideRepository.save(new FlagOverrideEntity(flagName, userId, state));
    }

    @Transactional
    public boolean deleteOverride(String flagName, String userId) {
        guard();
        FlagOverrideId id = new FlagOverrideId(flagName, userId);
        if (!overrideRepository.existsById(id)) {
            return false;
        }
        overrideRepository.deleteById(id);
        return true;
    }

    // ----- mapping -----

    private List<RuleEntity> toRuleEntities(List<Rule> rules) {
        if (rules == null) {
            return List.of();
        }
        return rules.stream()
                .map(r -> new RuleEntity(
                        r.attribute().key(),
                        r.operator().name(),
                        r.value(),
                        joinValues(r.values()),
                        r.result(),
                        r.order()))
                .toList();
    }

    private List<Rule> toDomainRules(FeatureFlagEntity entity) {
        return entity.getRules().stream()
                .sorted(Comparator.comparingInt(RuleEntity::getRuleOrder))
                .map(this::toDomainRule)
                .toList();
    }

    private Rule toDomainRule(RuleEntity e) {
        return new Rule(
                e.getRuleOrder(),
                Attribute.fromKey(e.getAttribute()),
                Operator.valueOf(e.getOperator()),
                e.getValue(),
                splitValues(e.getValues()),
                e.getResult());
    }

    private FlagRecord toRecord(FeatureFlagEntity entity) {
        return new FlagRecord(
                entity.getName(),
                entity.getDefaultState(),
                entity.getRolloutPercentage(),
                toDomainRules(entity),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private FlagDefinition toDefinition(FeatureFlagEntity entity, List<FlagOverrideEntity> overrides) {
        Map<String, Variation> overrideMap = new LinkedHashMap<>();
        for (FlagOverrideEntity o : overrides) {
            overrideMap.put(o.getUserId(), o.getState());
        }
        return new FlagDefinition(
                entity.getName(),
                entity.getDefaultState(),
                entity.getRolloutPercentage(),
                toDomainRules(entity),
                overrideMap);
    }

    private static String joinValues(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return String.join(",", values);
    }

    private static List<String> splitValues(String values) {
        if (values == null || values.isBlank()) {
            return List.of();
        }
        return List.of(values.split(","));
    }
}
