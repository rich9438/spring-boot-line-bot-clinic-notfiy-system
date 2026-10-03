package com.everythingcanbe.linebotclinicnotifysystem.eta;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.everythingcanbe.linebotclinicnotifysystem.config.NotificationProperties;
import com.everythingcanbe.linebotclinicnotifysystem.domain.RoomStatusHistory;
import com.everythingcanbe.linebotclinicnotifysystem.repository.RoomStatusHistoryRepository;

/**
 * 候診時間預估：ETA = 剩餘號碼 × 平均每號分鐘數（取當日、最近一段時間內的號碼變動紀錄）。
 */
@Service
public class WaitTimeEstimator {

    private final RoomStatusHistoryRepository historyRepository;
    private final NotificationProperties properties;
    private final Clock clock;

    public WaitTimeEstimator(RoomStatusHistoryRepository historyRepository, NotificationProperties properties,
            Clock clock) {
        this.historyRepository = historyRepository;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * @return 預估分鐘數；資料不足時為 empty
     */
    public Optional<Long> estimateMinutes(String providerCode, int roomId, int remaining) {
        if (remaining <= 0) {
            return Optional.empty();
        }
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime windowStart = now.minusMinutes(properties.eta().windowMinutes());
        LocalDateTime startOfDay = now.toLocalDate().atStartOfDay();
        LocalDateTime since = windowStart.isAfter(startOfDay) ? windowStart : startOfDay;

        List<RoomStatusHistory> records = historyRepository
                .findByProviderCodeAndRoomIdAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(providerCode, roomId,
                        since);
        return estimate(records, remaining, properties.eta().minAdvance());
    }

    static Optional<Long> estimate(List<RoomStatusHistory> records, int remaining, int minAdvance) {
        if (records.size() < 2) {
            return Optional.empty();
        }
        // 只採用最後一次號碼倒退（新診次或回頭叫號）之後的紀錄
        int start = 0;
        for (int i = 1; i < records.size(); i++) {
            if (records.get(i).getCurrentNumber() < records.get(i - 1).getCurrentNumber()) {
                start = i;
            }
        }
        RoomStatusHistory first = records.get(start);
        RoomStatusHistory last = records.getLast();
        int advance = last.getCurrentNumber() - first.getCurrentNumber();
        if (advance < minAdvance) {
            return Optional.empty();
        }
        double minutes = Duration.between(first.getCreatedAt(), last.getCreatedAt()).toSeconds() / 60.0;
        double minutesPerNumber = minutes / advance;
        return Optional.of(Math.round(remaining * minutesPerNumber));
    }

}
