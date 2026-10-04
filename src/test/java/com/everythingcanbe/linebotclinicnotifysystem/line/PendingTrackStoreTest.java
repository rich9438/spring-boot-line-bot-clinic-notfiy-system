package com.everythingcanbe.linebotclinicnotifysystem.line;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class PendingTrackStoreTest {

    private Instant now = Instant.parse("2026-10-04T02:00:00Z");

    private final PendingTrackStore store = new PendingTrackStore(new Clock() {
        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    });

    @Test
    void takeReturnsRoomOnce() {
        store.put("U1", 2);

        assertThat(store.take("U1")).contains(2);
        assertThat(store.take("U1")).isEmpty();
    }

    @Test
    void expiredSelectionIsIgnored() {
        store.put("U1", 2);
        now = now.plus(PendingTrackStore.TTL).plus(Duration.ofSeconds(1));

        assertThat(store.take("U1")).isEmpty();
    }

}
