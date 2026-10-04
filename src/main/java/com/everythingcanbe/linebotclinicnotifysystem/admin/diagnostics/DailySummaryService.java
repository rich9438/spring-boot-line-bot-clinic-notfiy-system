package com.everythingcanbe.linebotclinicnotifysystem.admin.diagnostics;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.everythingcanbe.linebotclinicnotifysystem.admin.ConditionalOnAdminEnabled;
import com.everythingcanbe.linebotclinicnotifysystem.domain.EndReason;
import com.everythingcanbe.linebotclinicnotifysystem.provider.RoomNames;
import com.everythingcanbe.linebotclinicnotifysystem.repository.NotificationHistoryRepository;
import com.everythingcanbe.linebotclinicnotifysystem.repository.RoomStatusHistoryRepository;
import com.everythingcanbe.linebotclinicnotifysystem.repository.SubscriberRepository;
import com.everythingcanbe.linebotclinicnotifysystem.repository.TrackingJobRepository;

/**
 * 每日摘要統計。
 */
@Service
@ConditionalOnAdminEnabled
public class DailySummaryService {

    private final TrackingJobRepository trackingJobRepository;
    private final NotificationHistoryRepository historyRepository;
    private final RoomStatusHistoryRepository roomStatusHistoryRepository;
    private final SubscriberRepository subscriberRepository;

    public DailySummaryService(TrackingJobRepository trackingJobRepository,
            NotificationHistoryRepository historyRepository, RoomStatusHistoryRepository roomStatusHistoryRepository,
            SubscriberRepository subscriberRepository) {
        this.trackingJobRepository = trackingJobRepository;
        this.historyRepository = historyRepository;
        this.roomStatusHistoryRepository = roomStatusHistoryRepository;
        this.subscriberRepository = subscriberRepository;
    }

    @Transactional(readOnly = true)
    public DailySummary summarize(LocalDate date) {
        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = date.plusDays(1).atStartOfDay();

        Map<EndReason, Long> ended = new EnumMap<>(EndReason.class);
        for (Object[] row : trackingJobRepository.countEndedByReason(from, to)) {
            ended.put((EndReason) row[0], (Long) row[1]);
        }
        long delivered = 0;
        long failed = 0;
        long unknown = 0;
        for (Object[] row : historyRepository.countPushedByDelivered(from, to)) {
            long count = (Long) row[1];
            if (Boolean.TRUE.equals(row[0])) {
                delivered += count;
            } else if (Boolean.FALSE.equals(row[0])) {
                failed += count;
            } else {
                unknown += count;
            }
        }
        List<RoomSession> rooms = roomStatusHistoryRepository.summarizeByRoom(from, to).stream()
                .map(row -> new RoomSession(RoomNames.of((Integer) row[0]), (LocalDateTime) row[1],
                        (LocalDateTime) row[2], (Integer) row[3], (Integer) row[4]))
                .toList();
        return new DailySummary(date,
                trackingJobRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(from, to),
                ended, delivered, failed, unknown, rooms,
                trackingJobRepository.findByActiveTrue().size(),
                subscriberRepository.countByFollowingTrue());
    }

    /**
     * @param pushedUnknown 推播結果未記錄（例如 V2 之前的資料）
     */
    public record DailySummary(LocalDate date, long newTrackings, Map<EndReason, Long> ended, long pushedDelivered,
            long pushedFailed, long pushedUnknown, List<RoomSession> rooms, long activeNow, long followers) {

        public long ended(EndReason reason) {
            return ended.getOrDefault(reason, 0L);
        }

    }

    public record RoomSession(String roomName, LocalDateTime firstAt, LocalDateTime lastAt, int minNumber,
            int maxNumber) {
    }

}
