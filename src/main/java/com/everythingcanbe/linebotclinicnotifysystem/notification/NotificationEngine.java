package com.everythingcanbe.linebotclinicnotifysystem.notification;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.everythingcanbe.linebotclinicnotifysystem.config.NotificationProperties;
import com.everythingcanbe.linebotclinicnotifysystem.domain.NotificationHistory;
import com.everythingcanbe.linebotclinicnotifysystem.domain.TrackingJob;
import com.everythingcanbe.linebotclinicnotifysystem.provider.RoomStatus;
import com.everythingcanbe.linebotclinicnotifysystem.repository.NotificationHistoryRepository;
import com.everythingcanbe.linebotclinicnotifysystem.repository.TrackingJobRepository;

/**
 * 診間號碼變動時，評估該診間所有 active 追蹤任務並推播通知。
 * 每個任務各自一個交易；推播在交易提交後才送出，推播失敗不回滾紀錄，避免重複推播。
 */
@Service
public class NotificationEngine {

    private static final Logger log = LoggerFactory.getLogger(NotificationEngine.class);

    private final List<NotificationRule> rules;
    private final TrackingJobRepository trackingJobRepository;
    private final NotificationHistoryRepository historyRepository;
    private final ThresholdResolver thresholdResolver;
    private final NotificationSender sender;
    private final NotificationProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public NotificationEngine(List<NotificationRule> rules, TrackingJobRepository trackingJobRepository,
            NotificationHistoryRepository historyRepository, ThresholdResolver thresholdResolver,
            NotificationSender sender, NotificationProperties properties, TransactionTemplate transactionTemplate,
            Clock clock) {
        this.rules = rules;
        this.trackingJobRepository = trackingJobRepository;
        this.historyRepository = historyRepository;
        this.thresholdResolver = thresholdResolver;
        this.sender = sender;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    public void evaluate(RoomStatus status, Integer previousNumber) {
        if (!status.inSession()) {
            return;
        }
        List<TrackingJob> jobs = trackingJobRepository
                .findByProviderCodeAndRoomIdAndActiveTrue(status.providerCode(), status.roomId());
        for (TrackingJob job : jobs) {
            try {
                Optional<PendingPush> push = transactionTemplate
                        .execute(tx -> evaluateJob(job.getId(), status, previousNumber));
                if (push != null) {
                    push.ifPresent(sender::send);
                }
            } catch (Exception e) {
                log.error("Failed to evaluate tracking job {}", job.getId(), e);
            }
        }
    }

    private Optional<PendingPush> evaluateJob(Long jobId, RoomStatus status, Integer previousNumber) {
        TrackingJob job = trackingJobRepository.findById(jobId).orElse(null);
        if (job == null || !job.isActive()) {
            return Optional.empty();
        }
        EvaluationContext context = new EvaluationContext(
                previousNumber,
                thresholdResolver.resolve(job.getSubscriber()),
                historyRepository.findThresholdsByTrackingJob(job),
                properties.sessionResetDrop());

        for (NotificationRule rule : rules) {
            Optional<NotificationDecision> decision = rule.evaluate(job, status, context);
            if (decision.isPresent()) {
                return Optional.of(apply(job, status, decision.get()));
            }
        }
        return Optional.empty();
    }

    private PendingPush apply(TrackingJob job, RoomStatus status, NotificationDecision decision) {
        LocalDateTime now = LocalDateTime.now(clock);
        Set<Integer> thresholds = decision.recordThresholds();
        for (Integer threshold : thresholds) {
            boolean pushed = threshold.equals(decision.pushedThreshold());
            historyRepository.save(new NotificationHistory(job, threshold, pushed, now));
        }
        if (decision.endReason() != null) {
            job.end(decision.endReason(), now);
        }
        log.info("Tracking job {} ({} #{}): {} at #{}", job.getId(), status.roomName(), job.getTargetNumber(),
                decision.type(), status.currentNumber());
        return new PendingPush(job.getSubscriber().getLineUserId(), decision.type(), status, job.getTargetNumber());
    }

}
