package com.everythingcanbe.linebotclinicnotifysystem.notification.rule;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.everythingcanbe.linebotclinicnotifysystem.domain.EndReason;
import com.everythingcanbe.linebotclinicnotifysystem.domain.TrackingJob;
import com.everythingcanbe.linebotclinicnotifysystem.notification.EvaluationContext;
import com.everythingcanbe.linebotclinicnotifysystem.notification.NotificationDecision;
import com.everythingcanbe.linebotclinicnotifysystem.notification.NotificationRule;
import com.everythingcanbe.linebotclinicnotifysystem.notification.NotificationType;
import com.everythingcanbe.linebotclinicnotifysystem.provider.RoomStatus;

/**
 * 剩餘人數跨越門檻：一次跨越多個門檻時只推播最小的一則，較大的門檻一併記錄。門檻 0 為到號並結束任務。
 */
@Component
@Order(3)
public class ThresholdReachedRule implements NotificationRule {

    @Override
    public Optional<NotificationDecision> evaluate(TrackingJob job, RoomStatus status, EvaluationContext context) {
        int remaining = job.getTargetNumber() - status.currentNumber();
        if (remaining < 0) {
            return Optional.empty();
        }
        Set<Integer> crossed = context.thresholds().stream()
                .filter(threshold -> threshold >= remaining)
                .filter(threshold -> !context.sentThresholds().contains(threshold))
                .collect(Collectors.toUnmodifiableSet());
        if (crossed.isEmpty()) {
            return Optional.empty();
        }
        int pushed = Collections.min(crossed);
        if (pushed == 0) {
            return Optional.of(new NotificationDecision(NotificationType.ARRIVED, crossed, 0, EndReason.ARRIVED));
        }
        return Optional.of(new NotificationDecision(NotificationType.PROGRESS, crossed, pushed, null));
    }

}
