package com.everythingcanbe.linebotclinicnotifysystem.notification;

public enum NotificationType {
    /** 已達門檻，即將輪到 */
    PROGRESS,
    /** 到號 */
    ARRIVED,
    /** 已過號 */
    MISSED,
    /** 號碼重置，新的診次開始 */
    SESSION_RESET
}
