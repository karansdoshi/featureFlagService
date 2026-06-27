package com.digitalocean.featureflags.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.digitalocean.featureflags.cache.FlagDefinitionCache;
import com.digitalocean.featureflags.domain.EvaluationContext;
import com.digitalocean.featureflags.domain.EvaluationResult;
import com.digitalocean.featureflags.domain.FlagDefinition;
import com.digitalocean.featureflags.domain.Reason;
import com.digitalocean.featureflags.domain.Variation;
import com.digitalocean.featureflags.evaluation.RolloutBucketer;
import com.digitalocean.featureflags.evaluation.RuleEngine;
import com.digitalocean.featureflags.storage.FlagStore;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith(MockitoExtension.class)
class EvaluationServiceTest {

    @Mock
    private FlagStore store;
    @Mock
    private FlagDefinitionCache cache;

    private EvaluationService service;

    private final EvaluationContext context = new EvaluationContext("u-1", "FREE", "US");

    @BeforeEach
    void setUp() {
        service = new EvaluationService(store, cache, new RuleEngine(new RolloutBucketer()));
    }

    private FlagDefinition definition(Variation dflt) {
        return new FlagDefinition("f", dflt, 0, List.of(), Map.of());
    }

    @Test
    void cacheHitIsServedWithoutTouchingTheStore() {
        when(cache.get("f")).thenReturn(Optional.of(definition(Variation.ON)));

        EvaluationResult result = service.evaluate("f", context);

        assertThat(result.enabled()).isTrue();
        assertThat(result.reason()).isEqualTo(Reason.DEFAULT);
        verify(store, never()).loadDefinition(any());
    }

    @Test
    void cacheMissLoadsFromStoreAndPopulatesCache() {
        when(cache.get("f")).thenReturn(Optional.empty());
        when(store.loadDefinition("f")).thenReturn(Optional.of(definition(Variation.ON)));

        EvaluationResult result = service.evaluate("f", context);

        assertThat(result.enabled()).isTrue();
        verify(cache).put(eq("f"), any(FlagDefinition.class));
    }

    @Test
    void unknownFlagOnEvaluateReturnsFallback() {
        when(cache.get("f")).thenReturn(Optional.empty());
        when(store.loadDefinition("f")).thenReturn(Optional.empty());

        EvaluationResult result = service.evaluate("f", context);

        assertThat(result.enabled()).isFalse();
        assertThat(result.reason()).isEqualTo(Reason.FALLBACK);
        verify(cache, never()).put(any(), any());
    }

    @Test
    void dbOutageOnCacheMissReturnsFallback() {
        when(cache.get("f")).thenReturn(Optional.empty());
        when(store.loadDefinition("f")).thenThrow(new DataAccessResourceFailureException("down"));

        EvaluationResult result = service.evaluate("f", context);

        assertThat(result.enabled()).isFalse();
        assertThat(result.reason()).isEqualTo(Reason.FALLBACK);
    }

    @Test
    void cachedFlagIsStillServedDuringOutage() {
        // Cache hit short-circuits before any store access, so an outage is invisible here.
        when(cache.get("f")).thenReturn(Optional.of(definition(Variation.ON)));

        EvaluationResult result = service.evaluate("f", context);

        assertThat(result.reason()).isEqualTo(Reason.DEFAULT);
        verify(store, never()).loadDefinition(any());
    }
}
