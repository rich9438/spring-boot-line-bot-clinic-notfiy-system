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
 * 號碼大幅倒退：視為新的診次開始，結束任務。小幅倒退（回頭叫過號病人）不在此列。
 */
@Component
@Order(1)
public class SessionResetRule implements NotificationRule {

    @Override
    public Optional<NotificationDecision> evaluate(TrackingJob job, RoomStatus status, EvaluationContext context) {
        Integer previous = context.previousNumber();
        if (previous == null || previous - status.currentNumber() < context.sessionResetDrop()) {
            return Optional.empty();
        }
        return Optional.of(new NotificationDecision(
                NotificationType.SESSION_RESET, Set.of(), null, EndReason.SESSION_RESET));
    }

}
