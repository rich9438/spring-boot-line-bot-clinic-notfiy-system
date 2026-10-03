package com.everythingcanbe.linebotclinicnotifysystem.notification;

import java.util.List;
import java.util.Set;

/**
 * 規則評估所需的額外資訊。
 *
 * @param previousNumber   上一次看診中的號碼，未知時為 null
 * @param thresholds       使用者有效門檻（由大到小，必含 0）
 * @param sentThresholds   此任務已處理過的門檻
 * @param sessionResetDrop 號碼倒退多少號以上視為新診次
 */
public record EvaluationContext(
        Integer previousNumber,
        List<Integer> thresholds,
        Set<Integer> sentThresholds,
        int sessionResetDrop) {
}
