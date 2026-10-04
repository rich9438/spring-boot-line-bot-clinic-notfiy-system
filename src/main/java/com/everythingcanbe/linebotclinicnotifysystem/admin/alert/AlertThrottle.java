package com.everythingcanbe.linebotclinicnotifysystem.admin.alert;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.everythingcanbe.linebotclinicnotifysystem.admin.AdminProperties;
import com.everythingcanbe.linebotclinicnotifysystem.admin.ConditionalOnAdminEnabled;

/**
 * 告警節流：同一 key 在設定的間隔內只放行一次。
 */
@Component
@ConditionalOnAdminEnabled
public class AlertThrottle {

    private final Map<String, Instant> lastSent = new ConcurrentHashMap<>();
    private final AdminProperties properties;
    private final Clock clock;

    public AlertThrottle(AdminProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public boolean tryAcquire(String key) {
        Instant now = clock.instant();
        boolean[] acquired = {false};
        lastSent.compute(key, (k, previous) -> {
            if (previous == null || !now.isBefore(previous.plus(properties.alerts().throttle()))) {
                acquired[0] = true;
                return now;
            }
            return previous;
        });
        return acquired[0];
    }

}
