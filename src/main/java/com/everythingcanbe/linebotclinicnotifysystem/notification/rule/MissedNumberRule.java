package com.everythingcanbe.linebotclinicnotifysystem.notification.rule;

import java.util.Optional;
import java.util.Set;

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
 * 目前號碼已超過使用者號碼，且未曾送出到號通知（例如號碼直接跳過）。
 */
@Component
@Order(2)
public class MissedNumberRule implements NotificationRule {

    @Override
    public Optional<NotificationDecision> evaluate(TrackingJob job, RoomStatus status, EvaluationContext context) {
        int remaining = job.getTargetNumber() - status.currentNumber();
        if (remaining >= 0 || context.sentThresholds().contains(0)) {
            return Optional.empty();
        }
        return Optional.of(new NotificationDecision(NotificationType.MISSED, Set.of(), null, EndReason.MISSED));
    }

}
