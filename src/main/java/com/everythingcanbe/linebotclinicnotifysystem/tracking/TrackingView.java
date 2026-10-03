package com.everythingcanbe.linebotclinicnotifysystem.tracking;

import java.util.Optional;

import com.everythingcanbe.linebotclinicnotifysystem.provider.RoomStatus;

/**
 * 「目前狀態」查詢結果。
 *
 * @param status 診間狀態；暫時無法取得時為 empty
 */
public record TrackingView(
        int roomId,
        int targetNumber,
        Optional<RoomStatus> status,
        Optional<Long> etaMinutes) {
}
