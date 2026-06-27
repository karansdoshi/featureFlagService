package com.digitalocean.featureflags.storage;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface FlagOverrideRepository extends JpaRepository<FlagOverrideEntity, FlagOverrideId> {

    List<FlagOverrideEntity> findByFlagName(String flagName);

    @Transactional
    void deleteByFlagName(String flagName);
}
