package com.everythingcanbe.linebotclinicnotifysystem.tracking;

import java.util.List;

/**
 * 使用者的通知門檻設定。
 *
 * @param thresholds 目前有效門檻（由大到小，必含 0）
 * @param custom     是否為自訂門檻
 * @param defaults   系統預設門檻
 */
public record ThresholdSettings(List<Integer> thresholds, boolean custom, List<Integer> defaults) {
}
