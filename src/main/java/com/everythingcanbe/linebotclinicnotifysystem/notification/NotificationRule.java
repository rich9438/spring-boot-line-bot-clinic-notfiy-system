package com.everythingcanbe.linebotclinicnotifysystem.notification;

import java.util.Optional;

import com.everythingcanbe.linebotclinicnotifysystem.domain.TrackingJob;
import com.everythingcanbe.linebotclinicnotifysystem.provider.RoomStatus;

/**
 * 通知規則。規則依 {@link org.springframework.core.annotation.Order} 排序，命中第一條即停止評估。
 */
public interface NotificationRule {

    Optional<NotificationDecision> evaluate(TrackingJob job, RoomStatus status, EvaluationContext context);

}
