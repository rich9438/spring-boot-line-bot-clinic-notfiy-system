package com.everythingcanbe.linebotclinicnotifysystem.tracking;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.everythingcanbe.linebotclinicnotifysystem.domain.EndReason;
import com.everythingcanbe.linebotclinicnotifysystem.domain.NotificationHistory;
import com.everythingcanbe.linebotclinicnotifysystem.domain.Subscriber;
import com.everythingcanbe.linebotclinicnotifysystem.domain.SubscriberThreshold;
import com.everythingcanbe.linebotclinicnotifysystem.domain.TrackingJob;
import com.everythingcanbe.linebotclinicnotifysystem.eta.WaitTimeEstimator;
import com.everythingcanbe.linebotclinicnotifysystem.monitor.RoomStatusQueryService;
import com.everythingcanbe.linebotclinicnotifysystem.notification.ThresholdResolver;
import com.everythingcanbe.linebotclinicnotifysystem.provider.RoomStatus;
import com.everythingcanbe.linebotclinicnotifysystem.repository.NotificationHistoryRepository;
import com.everythingcanbe.linebotclinicnotifysystem.repository.SubscriberRepository;
import com.everythingcanbe.linebotclinicnotifysystem.repository.SubscriberThresholdRepository;
import com.everythingcanbe.linebotclinicnotifysystem.repository.TrackingJobRepository;

/**
 * 追蹤任務與通知門檻管理。每位使用者同一時間只有一個 active 追蹤任務。
 */
@Service
public class TrackingService {

    public static final int MAX_CUSTOM_THRESHOLDS = 5;
    public static final int MAX_THRESHOLD = 50;

    private final SubscriberService subscriberService;
    private final SubscriberRepository subscriberRepository;
    private final TrackingJobRepository trackingJobRepository;
    private final SubscriberThresholdRepository thresholdRepository;
    private final NotificationHistoryRepository historyRepository;
    private final ThresholdResolver thresholdResolver;
    private final RoomStatusQueryService roomStatusQuery;
    private final WaitTimeEstimator waitTimeEstimator;
    private final Clock clock;

    public TrackingService(SubscriberService subscriberService, SubscriberRepository subscriberRepository,
            TrackingJobRepository trackingJobRepository, SubscriberThresholdRepository thresholdRepository,
            NotificationHistoryRepository historyRepository, ThresholdResolver thresholdResolver,
            RoomStatusQueryService roomStatusQuery, WaitTimeEstimator waitTimeEstimator, Clock clock) {
        this.subscriberService = subscriberService;
        this.subscriberRepository = subscriberRepository;
        this.trackingJobRepository = trackingJobRepository;
        this.thresholdRepository = thresholdRepository;
        this.historyRepository = historyRepository;
        this.thresholdResolver = thresholdResolver;
        this.roomStatusQuery = roomStatusQuery;
        this.waitTimeEstimator = waitTimeEstimator;
        this.clock = clock;
    }

    @Transactional
    public StartTrackingResult start(String lineUserId, int roomId, int targetNumber) {
        Optional<RoomStatus> current = roomStatusQuery.current(roomId);
        if (current.isEmpty()) {
            return new StartTrackingResult.RoomUnavailable(roomId);
        }
        RoomStatus status = current.get();
        if (!status.inSession()) {
            return new StartTrackingResult.NotInSession(status);
        }
        int remaining = targetNumber - status.currentNumber();
        if (remaining <= 0) {
            return new StartTrackingResult.NumberReached(status, targetNumber);
        }

        Subscriber subscriber = subscriberService.getOrCreate(lineUserId);
        boolean replaced = endActiveJobs(subscriber, EndReason.REPLACED) > 0;
        TrackingJob job = trackingJobRepository.save(new TrackingJob(subscriber, roomStatusQuery.providerCode(),
                roomId, targetNumber, LocalDateTime.now(clock)));

        List<Integer> thresholds = thresholdResolver.resolve(subscriber);
        markCrossedThresholdsSilently(job, thresholds, remaining);
        Optional<Long> eta = waitTimeEstimator.estimateMinutes(job.getProviderCode(), roomId, remaining);
        return new StartTrackingResult.Started(status, targetNumber, thresholds, eta, replaced);
    }

    @Transactional(readOnly = true)
    public Optional<TrackingView> currentTracking(String lineUserId) {
        return activeJob(lineUserId).map(job -> {
            Optional<RoomStatus> status = roomStatusQuery.current(job.getRoomId());
            Optional<Long> eta = status.filter(RoomStatus::inSession)
                    .flatMap(s -> waitTimeEstimator.estimateMinutes(job.getProviderCode(), job.getRoomId(),
                            job.getTargetNumber() - s.currentNumber()));
            return new TrackingView(job.getRoomId(), job.getTargetNumber(), status, eta);
        });
    }

    /**
     * @return 是否有任務被取消
     */
    @Transactional
    public boolean cancel(String lineUserId) {
        return subscriberRepository.findByLineUserId(lineUserId)
                .map(subscriber -> endActiveJobs(subscriber, EndReason.CANCELLED) > 0)
                .orElse(false);
    }

    @Transactional
    public ThresholdSettings thresholdSettings(String lineUserId) {
        return settingsOf(subscriberService.getOrCreate(lineUserId));
    }

    /**
     * 設定自訂門檻（0 會自動加入）。進行中的任務若已在新門檻內，該門檻直接標記為已處理，不補發通知。
     *
     * @throws IllegalArgumentException 門檻值不合法
     */
    @Transactional
    public ThresholdSettings updateThresholds(String lineUserId, List<Integer> values) {
        List<Integer> positives = values.stream().filter(v -> v != 0).distinct().toList();
        if (positives.isEmpty()) {
            throw new IllegalArgumentException("請至少輸入一個門檻");
        }
        if (positives.size() > MAX_CUSTOM_THRESHOLDS) {
            throw new IllegalArgumentException("門檻最多 " + MAX_CUSTOM_THRESHOLDS + " 個");
        }
        if (positives.stream().anyMatch(v -> v < 1 || v > MAX_THRESHOLD)) {
            throw new IllegalArgumentException("門檻需介於 1～" + MAX_THRESHOLD);
        }

        Subscriber subscriber = subscriberService.getOrCreate(lineUserId);
        thresholdRepository.deleteBySubscriber(subscriber);
        LocalDateTime now = LocalDateTime.now(clock);
        thresholdRepository.saveAll(ThresholdResolver.normalize(positives).stream()
                .map(threshold -> new SubscriberThreshold(subscriber, threshold, now))
                .toList());

        List<Integer> thresholds = thresholdResolver.resolve(subscriber);
        trackingJobRepository.findBySubscriberAndActiveTrue(subscriber).forEach(job ->
                roomStatusQuery.current(job.getRoomId())
                        .filter(RoomStatus::inSession)
                        .ifPresent(status -> markCrossedThresholdsSilently(job, thresholds,
                                job.getTargetNumber() - status.currentNumber())));
        return settingsOf(subscriber);
    }

    @Transactional
    public ThresholdSettings resetThresholds(String lineUserId) {
        Subscriber subscriber = subscriberService.getOrCreate(lineUserId);
        thresholdRepository.deleteBySubscriber(subscriber);
        return settingsOf(subscriber);
    }

    private ThresholdSettings settingsOf(Subscriber subscriber) {
        return new ThresholdSettings(thresholdResolver.resolve(subscriber), thresholdResolver.hasCustom(subscriber),
                thresholdResolver.defaults());
    }

    @Transactional
    public void unfollow(String lineUserId) {
        subscriberRepository.findByLineUserId(lineUserId).ifPresent(subscriber -> {
            subscriber.setFollowing(false);
            endActiveJobs(subscriber, EndReason.UNFOLLOWED);
        });
    }

    /**
     * 結束所有 active 任務（每日清除）。
     */
    @Transactional
    public int expireAll() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<TrackingJob> jobs = trackingJobRepository.findByActiveTrue();
        jobs.forEach(job -> job.end(EndReason.EXPIRED, now));
        return jobs.size();
    }

    private Optional<TrackingJob> activeJob(String lineUserId) {
        return subscriberRepository.findByLineUserId(lineUserId)
                .flatMap(subscriber -> trackingJobRepository.findBySubscriberAndActiveTrue(subscriber).stream()
                        .findFirst());
    }

    private int endActiveJobs(Subscriber subscriber, EndReason reason) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<TrackingJob> jobs = trackingJobRepository.findBySubscriberAndActiveTrue(subscriber);
        jobs.forEach(job -> job.end(reason, now));
        return jobs.size();
    }

    /**
     * 已在門檻內（threshold ≥ remaining）的正數門檻記為已處理但不推播。
     */
    private void markCrossedThresholdsSilently(TrackingJob job, List<Integer> thresholds, int remaining) {
        Set<Integer> sent = historyRepository.findThresholdsByTrackingJob(job);
        LocalDateTime now = LocalDateTime.now(clock);
        thresholds.stream()
                .filter(threshold -> threshold > 0 && threshold >= remaining && !sent.contains(threshold))
                .forEach(threshold -> historyRepository.save(new NotificationHistory(job, threshold, false, now)));
    }

}
