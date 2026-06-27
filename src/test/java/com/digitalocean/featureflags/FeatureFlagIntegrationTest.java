package com.digitalocean.featureflags;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.digitalocean.featureflags.cache.FlagDefinitionCache;
import com.digitalocean.featureflags.storage.StorageProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FeatureFlagIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private StorageProperties storageProperties;
    @Autowired
    private FlagDefinitionCache cache;

    @AfterEach
    void reset() {
        storageProperties.setSimulateOutage(false);
        cache.clear();
    }

    private void createFlag(String body) throws Exception {
        mvc.perform(post("/api/flags").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void flagCrudLifecycle() throws Exception {
        String body = """
                {"name":"life","defaultState":"OFF","rolloutPercentage":0,"rules":[]}""";
        createFlag(body);

        mvc.perform(get("/api/flags/life")).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("life"))
                .andExpect(jsonPath("$.defaultState").value("OFF"));

        // Duplicate create -> 409.
        mvc.perform(post("/api/flags").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());

        mvc.perform(delete("/api/flags/life")).andExpect(status().isNoContent());
        mvc.perform(get("/api/flags/life")).andExpect(status().isNotFound());
    }

    @Test
    void evaluateMatchesRuleThenFallsThroughToDefault() throws Exception {
        createFlag("""
                {"name":"checkout","defaultState":"OFF","rolloutPercentage":0,
                 "rules":[{"attribute":"subscriptionTier","operator":"EQUALS",
                           "value":"PREMIUM","result":"ON","order":1}]}""");

        mvc.perform(post("/api/flags/checkout/evaluate").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"u-1","subscriptionTier":"PREMIUM","region":"US"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.reason").value("RULE_MATCH"));

        mvc.perform(post("/api/flags/checkout/evaluate").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"u-1","subscriptionTier":"FREE","region":"US"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.reason").value("DEFAULT"));
    }

    @Test
    void unknownFlagOnEvaluateReturnsNotFound() throws Exception {
        mvc.perform(post("/api/flags/does-not-exist/evaluate").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"u-1","subscriptionTier":"FREE","region":"US"}"""))
                .andExpect(status().isNotFound());
    }

    @Test
    void missingContextFieldIsRejected() throws Exception {
        mvc.perform(post("/api/flags/any/evaluate").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"u-1","region":"US"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownPropertyIsRejected() throws Exception {
        mvc.perform(post("/api/flags/any/evaluate").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"u-1","subscriptionTier":"FREE","region":"US","bogus":"x"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidRolloutPercentageIsRejected() throws Exception {
        mvc.perform(post("/api/flags").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"bad","defaultState":"OFF","rolloutPercentage":150,"rules":[]}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownOperatorIsRejected() throws Exception {
        mvc.perform(post("/api/flags").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"badop","defaultState":"OFF","rolloutPercentage":0,
                                 "rules":[{"attribute":"region","operator":"GIBBERISH",
                                           "value":"US","result":"ON","order":1}]}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void overrideTakesEffectImmediatelyAcrossCache() throws Exception {
        createFlag("""
                {"name":"ovr","defaultState":"OFF","rolloutPercentage":0,"rules":[]}""");

        // First evaluation populates the cache with the OFF definition.
        mvc.perform(post("/api/flags/ovr/evaluate").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"u-1","subscriptionTier":"FREE","region":"US"}"""))
                .andExpect(jsonPath("$.reason").value("DEFAULT"));

        mvc.perform(put("/api/flags/ovr/overrides/u-1").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"state":"ON"}"""))
                .andExpect(status().isNoContent());

        // If invalidation failed, this would still serve the cached OFF/DEFAULT.
        mvc.perform(post("/api/flags/ovr/evaluate").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"u-1","subscriptionTier":"FREE","region":"US"}"""))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.reason").value("OVERRIDE"));
    }

    @Test
    void updateInvalidatesCache() throws Exception {
        createFlag("""
                {"name":"upd","defaultState":"OFF","rolloutPercentage":0,"rules":[]}""");

        mvc.perform(post("/api/flags/upd/evaluate").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"u-1","subscriptionTier":"FREE","region":"US"}"""))
                .andExpect(jsonPath("$.enabled").value(false));

        mvc.perform(put("/api/flags/upd").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"defaultState":"ON","rolloutPercentage":0,"rules":[]}"""))
                .andExpect(status().isOk());

        mvc.perform(post("/api/flags/upd/evaluate").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"u-1","subscriptionTier":"FREE","region":"US"}"""))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.reason").value("DEFAULT"));
    }

    @Test
    void fullRolloutYieldsRolloutReason() throws Exception {
        createFlag("""
                {"name":"roll","defaultState":"OFF","rolloutPercentage":100,"rules":[]}""");

        mvc.perform(post("/api/flags/roll/evaluate").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"u-9","subscriptionTier":"FREE","region":"US"}"""))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.reason").value("ROLLOUT"));
    }

    @Test
    void outageServesCachedFlagButFallsBackForUncachedAndFailsWrites() throws Exception {
        createFlag("""
                {"name":"out","defaultState":"ON","rolloutPercentage":0,"rules":[]}""");

        // Cache the definition while the DB is healthy.
        mvc.perform(post("/api/flags/out/evaluate").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"u-1","subscriptionTier":"FREE","region":"US"}"""))
                .andExpect(jsonPath("$.enabled").value(true));

        storageProperties.setSimulateOutage(true);

        // Cached flag is still served (degraded-but-available).
        mvc.perform(post("/api/flags/out/evaluate").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"u-1","subscriptionTier":"FREE","region":"US"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.reason").value("DEFAULT"));

        // Uncached flag during outage -> FALLBACK (200).
        mvc.perform(post("/api/flags/uncached/evaluate").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"u-1","subscriptionTier":"FREE","region":"US"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reason").value("FALLBACK"));

        // Writes during outage -> 503.
        mvc.perform(post("/api/flags").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"new","defaultState":"OFF","rolloutPercentage":0,"rules":[]}"""))
                .andExpect(status().isServiceUnavailable());
    }
}
