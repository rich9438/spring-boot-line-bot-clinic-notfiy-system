package com.everythingcanbe.linebotclinicnotifysystem.notification;

import java.util.Set;

import com.everythingcanbe.linebotclinicnotifysystem.domain.EndReason;

/**
 * 規則評估結果。
 *
 * @param type             要推播的訊息類型
 * @param recordThresholds 要寫入 notification_history 的門檻
 * @param pushedThreshold  實際推播的門檻（其餘門檻記為靜默略過），無則為 null
 * @param endReason        需結束任務時的原因，否則為 null
 */
public record NotificationDecision(
        NotificationType type,
        Set<Integer> recordThresholds,
        Integer pushedThreshold,
        EndReason endReason) {
}
