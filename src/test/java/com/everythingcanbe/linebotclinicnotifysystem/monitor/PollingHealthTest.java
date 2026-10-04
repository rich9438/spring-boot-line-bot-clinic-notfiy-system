package com.everythingcanbe.linebotclinicnotifysystem.monitor;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.everythingcanbe.linebotclinicnotifysystem.provider.RoomStatus;

class PollingHealthTest {

    private final List<Object> events = new ArrayList<>();
    private final PollingHealth health = new PollingHealth(events::add, Clock.system(ZoneId.of("Asia/Taipei")));

    @Test
    void countsConsecutiveFailuresAndPublishesRecovery() {
        List<Integer> rooms = List.of(1, 2);

        health.record(rooms, List.of(room(1), room(2)));
        health.record(rooms, List.of(room(1)));
        health.record(rooms, List.of(room(1)));

        assertThat(health.get(2).consecutiveFailures()).isEqualTo(2);
        assertThat(health.get(2).lastSuccessAt()).isNotNull();
        assertThat(events).containsExactly(new RoomFetchFailedEvent(2, 1), new RoomFetchFailedEvent(2, 2));

        health.record(rooms, List.of(room(1), room(2)));

        assertThat(health.get(2).consecutiveFailures()).isZero();
        assertThat(events).last().isEqualTo(new RoomFetchRecoveredEvent(2, 2));
    }

    private static RoomStatus room(int roomId) {
        return new RoomStatus("wuobs", roomId, "診", null, null, 10, true, LocalDateTime.now());
    }

}
