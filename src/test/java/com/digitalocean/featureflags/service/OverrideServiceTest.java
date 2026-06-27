package com.digitalocean.featureflags.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.digitalocean.featureflags.cache.FlagDefinitionCache;
import com.digitalocean.featureflags.domain.Variation;
import com.digitalocean.featureflags.error.FlagNotFoundException;
import com.digitalocean.featureflags.storage.FlagStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OverrideServiceTest {

    @Mock
    private FlagStore store;
    @Mock
    private FlagDefinitionCache cache;
    @InjectMocks
    private OverrideService service;

    @Test
    void setOverrideEvictsCache() {
        when(store.exists("f")).thenReturn(true);

        service.set("f", "u-1", Variation.ON);

        verify(store).putOverride("f", "u-1", Variation.ON);
        verify(cache).evict("f");
    }

    @Test
    void setOverrideOnUnknownFlagFailsWithoutWriting() {
        when(store.exists("f")).thenReturn(false);

        assertThatThrownBy(() -> service.set("f", "u-1", Variation.ON))
                .isInstanceOf(FlagNotFoundException.class);

        verify(store, never()).putOverride(any(), any(), any());
        verify(cache, never()).evict(any());
    }

    @Test
    void removeOverrideEvictsCache() {
        when(store.exists("f")).thenReturn(true);

        service.remove("f", "u-1");

        verify(store).deleteOverride("f", "u-1");
        verify(cache).evict("f");
    }
}
