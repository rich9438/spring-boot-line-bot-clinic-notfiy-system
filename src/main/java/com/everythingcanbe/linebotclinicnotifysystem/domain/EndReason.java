package com.everythingcanbe.linebotclinicnotifysystem.domain;

/**
 * 追蹤任務結束原因。
 */
public enum EndReason {
    /** 已送出到號通知 */
    ARRIVED,
    /** 偵測到已過號 */
    MISSED,
    /** 使用者取消 */
    CANCELLED,
    /** 被新的追蹤取代 */
    REPLACED,
    /** 號碼大幅倒退，視為新的診次 */
    SESSION_RESET,
    /** 每日清除 */
    EXPIRED,
    /** 使用者封鎖官方帳號 */
    UNFOLLOWED
}
