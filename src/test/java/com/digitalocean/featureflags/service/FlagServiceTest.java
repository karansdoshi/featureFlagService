package com.digitalocean.featureflags.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.digitalocean.featureflags.cache.FlagDefinitionCache;
import com.digitalocean.featureflags.domain.Variation;
import com.digitalocean.featureflags.error.DuplicateFlagException;
import com.digitalocean.featureflags.storage.FlagRecord;
import com.digitalocean.featureflags.storage.FlagStore;
import com.digitalocean.featureflags.storage.FlagWrite;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FlagServiceTest {

    @Mock
    private FlagStore store;
    @Mock
    private FlagDefinitionCache cache;
    @InjectMocks
    private FlagService service;

    private FlagWrite write(String name) {
        return new FlagWrite(name, Variation.OFF, 0, List.of());
    }

    private FlagRecord record(String name) {
        return new FlagRecord(name, Variation.OFF, 0, List.of(), Instant.now(), Instant.now());
    }

    @Test
    void createEvictsCacheAfterPersisting() {
        when(store.exists("f")).thenReturn(false);
        when(store.create(any())).thenReturn(record("f"));

        service.create(write("f"));

        verify(store).create(any());
        verify(cache).evict("f");
    }

    @Test
    void createRejectsDuplicateWithoutPersistingOrEvicting() {
        when(store.exists("f")).thenReturn(true);

        assertThatThrownBy(() -> service.create(write("f")))
                .isInstanceOf(DuplicateFlagException.class);

        verify(store, never()).create(any());
        verify(cache, never()).evict(any());
    }

    @Test
    void updateEvictsCache() {
        when(store.update(eq("f"), any())).thenReturn(record("f"));

        service.update("f", write("f"));

        verify(store).update(eq("f"), any());
        verify(cache).evict("f");
    }

    @Test
    void deleteEvictsCache() {
        service.delete("f");

        verify(store).delete("f");
        verify(cache).evict("f");
    }
}
