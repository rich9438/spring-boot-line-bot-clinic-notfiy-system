package com.everythingcanbe.linebotclinicnotifysystem.line;

/**
 * 前台推播失敗（後台告警使用）。
 */
public record PushFailedEvent(String lineUserId, String error) {
}
