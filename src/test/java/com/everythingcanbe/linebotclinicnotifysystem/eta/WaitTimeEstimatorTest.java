package com.everythingcanbe.linebotclinicnotifysystem.eta;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.everythingcanbe.linebotclinicnotifysystem.domain.RoomStatusHistory;

class WaitTimeEstimatorTest {

    private static final LocalDateTime BASE = LocalDateTime.of(2026, 10, 3, 14, 0);

    @Test
    void estimatesFromAverageMinutesPerNumber() {
        List<RoomStatusHistory> records = List.of(record(0, 40), record(10, 44), record(20, 48));

        // 20 分鐘 / 8 號 = 2.5 分鐘/號，剩 8 位 → 20 分鐘
        assertThat(WaitTimeEstimator.estimate(records, 8, 3)).contains(20L);
    }

    @Test
    void insufficientAdvanceReturnsEmpty() {
        List<RoomStatusHistory> records = List.of(record(0, 40), record(10, 42));

        assertThat(WaitTimeEstimator.estimate(records, 8, 3)).isEmpty();
    }

    @Test
    void singleRecordReturnsEmpty() {
        assertThat(WaitTimeEstimator.estimate(List.of(record(0, 40)), 8, 3)).isEmpty();
    }

    @Test
    void onlyUsesRecordsAfterLastDrop() {
        List<RoomStatusHistory> records = List.of(
                record(0, 50), record(5, 60), record(10, 1), record(20, 5), record(30, 9));

        // 重置後：20 分鐘 / 8 號 = 2.5 分鐘/號
        assertThat(WaitTimeEstimator.estimate(records, 4, 3)).contains(10L);
    }

    private static RoomStatusHistory record(int minutes, int number) {
        return new RoomStatusHistory("wuobs", 2, number, null, null, null, BASE.plusMinutes(minutes));
    }

}
