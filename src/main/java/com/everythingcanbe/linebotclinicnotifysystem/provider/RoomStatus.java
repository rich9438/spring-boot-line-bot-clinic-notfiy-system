package com.everythingcanbe.linebotclinicnotifysystem.provider;

import java.time.LocalDateTime;

/**
 * 單一診間的即時狀態。
 *
 * @param currentNumber 目前看診號碼；未看診時為 null
 * @param inSession     是否看診中（號碼有效且資料為當日）
 * @param updateTime    資料來源的更新時間
 */
public record RoomStatus(
        String providerCode,
        Integer roomId,
        String roomName,
        String doctorName,
        String department,
        Integer currentNumber,
        boolean inSession,
        LocalDateTime updateTime) {
}
