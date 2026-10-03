package com.everythingcanbe.linebotclinicnotifysystem.monitor;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.everythingcanbe.linebotclinicnotifysystem.domain.RoomStatusHistory;
import com.everythingcanbe.linebotclinicnotifysystem.notification.NotificationEngine;
import com.everythingcanbe.linebotclinicnotifysystem.provider.RoomStatus;
import com.everythingcanbe.linebotclinicnotifysystem.repository.RoomStatusHistoryRepository;

/**
 * 處理每次抓到的診間狀態：比對快取 → 有變動時寫入歷史並觸發通知引擎。
 */
@Service
public class RoomStatusMonitor {

    private static final Logger log = LoggerFactory.getLogger(RoomStatusMonitor.class);

    private final RoomStatusCache cache;
    private final RoomStatusHistoryRepository historyRepository;
    private final NotificationEngine notificationEngine;
    private final Clock clock;

    public RoomStatusMonitor(RoomStatusCache cache, RoomStatusHistoryRepository historyRepository,
            NotificationEngine notificationEngine, Clock clock) {
        this.cache = cache;
        this.historyRepository = historyRepository;
        this.notificationEngine = notificationEngine;
        this.clock = clock;
    }

    public void onStatus(RoomStatus status) {
        Integer previousNumber = lastKnownNumber(status);
        RoomStatus previous = cache.put(status);

        boolean changed = previous == null
                || previous.inSession() != status.inSession()
                || !Objects.equals(previous.currentNumber(), status.currentNumber());
        if (!changed) {
            return;
        }
        log.info("{} changed: {} -> {} (inSession={})", status.roomName(),
                previous == null ? "-" : previous.currentNumber(), status.currentNumber(), status.inSession());

        if (status.inSession() && !Objects.equals(previousNumber, status.currentNumber())) {
            historyRepository.save(new RoomStatusHistory(status.providerCode(), status.roomId(),
                    status.currentNumber(), status.doctorName(), status.department(), status.updateTime(),
                    LocalDateTime.now(clock)));
        }
        notificationEngine.evaluate(status, previousNumber);
    }

    /**
     * 上一次看診中的號碼。應用程式重啟後快取為空，改用當日最後一筆歷史紀錄。
     */
    private Integer lastKnownNumber(RoomStatus status) {
        return cache.lastInSessionNumber(status.roomId())
                .orElseGet(() -> historyRepository
                        .findFirstByProviderCodeAndRoomIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                                status.providerCode(), status.roomId(), LocalDate.now(clock).atStartOfDay())
                        .map(RoomStatusHistory::getCurrentNumber)
                        .orElse(null));
    }

}
