package com.everythingcanbe.linebotclinicnotifysystem.admin.diagnostics;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.everythingcanbe.linebotclinicnotifysystem.admin.ConditionalOnAdminEnabled;
import com.everythingcanbe.linebotclinicnotifysystem.domain.EndReason;
import com.everythingcanbe.linebotclinicnotifysystem.domain.Subscriber;
import com.everythingcanbe.linebotclinicnotifysystem.domain.TrackingJob;
import com.everythingcanbe.linebotclinicnotifysystem.provider.RoomNames;
import com.everythingcanbe.linebotclinicnotifysystem.repository.NotificationHistoryRepository;
import com.everythingcanbe.linebotclinicnotifysystem.repository.SubscriberRepository;
import com.everythingcanbe.linebotclinicnotifysystem.repository.TrackingJobRepository;

/**
 * 後台「查詢」指令：依 userId 或顯示名稱找使用者，列出追蹤與推播紀錄（客服排查「沒收到通知」）。
 */
@Service
@ConditionalOnAdminEnabled
public class SubscriberLookupService {

    private static final String USER_ID_PATTERN = "U[0-9a-f]{32}";

    private final SubscriberRepository subscriberRepository;
    private final TrackingJobRepository trackingJobRepository;
    private final NotificationHistoryRepository historyRepository;

    public SubscriberLookupService(SubscriberRepository subscriberRepository,
            TrackingJobRepository trackingJobRepository, NotificationHistoryRepository historyRepository) {
        this.subscriberRepository = subscriberRepository;
        this.trackingJobRepository = trackingJobRepository;
        this.historyRepository = historyRepository;
    }

    @Transactional(readOnly = true)
    public LookupResult lookup(String query) {
        List<Subscriber> matches = query.matches(USER_ID_PATTERN)
                ? subscriberRepository.findByLineUserId(query).map(List::of).orElse(List.of())
                : subscriberRepository.findTop6ByDisplayNameContainingIgnoreCaseOrderByCreatedAtDesc(query);
        if (matches.isEmpty()) {
            return new LookupResult.NotFound(query);
        }
        if (matches.size() > 1) {
            return new LookupResult.Multiple(query, matches.stream().map(SubscriberLookupService::summary).toList());
        }
        Subscriber subscriber = matches.getFirst();
        List<TrackingJob> jobs = trackingJobRepository.findTop5BySubscriberOrderByCreatedAtDesc(subscriber);
        List<NotificationLine> notifications = jobs.isEmpty() ? List.of()
                : historyRepository.findTop10ByTrackingJobInAndPushedTrueOrderBySentAtDesc(jobs).stream()
                        .map(history -> new NotificationLine(RoomNames.of(history.getTrackingJob().getRoomId()),
                                history.getTrackingJob().getTargetNumber(), history.getThreshold(),
                                history.getSentAt(), history.getDelivered()))
                        .toList();
        List<JobLine> jobLines = jobs.stream()
                .map(job -> new JobLine(RoomNames.of(job.getRoomId()), job.getTargetNumber(), job.isActive(),
                        job.getEndReason(), job.getCreatedAt(), job.getEndedAt()))
                .toList();
        return new LookupResult.Found(summary(subscriber), subscriber.getCreatedAt(), jobLines, notifications);
    }

    private static SubscriberSummary summary(Subscriber subscriber) {
        return new SubscriberSummary(Optional.ofNullable(subscriber.getDisplayName()).orElse("（未取得名稱）"),
                subscriber.getLineUserId(), subscriber.isFollowing());
    }

    public sealed interface LookupResult {

        record NotFound(String query) implements LookupResult {
        }

        /** 符合多位使用者（最多列出 6 位） */
        record Multiple(String query, List<SubscriberSummary> subscribers) implements LookupResult {
        }

        record Found(SubscriberSummary subscriber, LocalDateTime createdAt, List<JobLine> jobs,
                List<NotificationLine> notifications) implements LookupResult {
        }

    }

    public record SubscriberSummary(String displayName, String lineUserId, boolean following) {
    }

    public record JobLine(String roomName, int targetNumber, boolean active, EndReason endReason,
            LocalDateTime createdAt, LocalDateTime endedAt) {
    }

    /**
     * @param delivered 推播結果：true 送達、false 失敗、null 未記錄
     */
    public record NotificationLine(String roomName, int targetNumber, int threshold, LocalDateTime sentAt,
            Boolean delivered) {
    }

}
