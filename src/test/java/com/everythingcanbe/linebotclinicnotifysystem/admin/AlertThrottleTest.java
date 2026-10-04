package com.everythingcanbe.linebotclinicnotifysystem.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.everythingcanbe.linebotclinicnotifysystem.admin.alert.AlertThrottle;

class AlertThrottleTest {

    private Instant now = Instant.parse("2026-10-05T02:00:00Z");

    private final AlertThrottle throttle = new AlertThrottle(
            new AdminProperties(true, "t", "s", List.of(), new AdminProperties.Alerts(5, Duration.ofMinutes(30), 0.8)),
            new Clock() {
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
    void sameKeyIsThrottledWithinInterval() {
        assertThat(throttle.tryAcquire("push-failed")).isTrue();
        assertThat(throttle.tryAcquire("push-failed")).isFalse();
        assertThat(throttle.tryAcquire("fetch-failed:2")).isTrue();

        now = now.plus(Duration.ofMinutes(30));
        assertThat(throttle.tryAcquire("push-failed")).isTrue();
    }

}
