package com.everythingcanbe.linebotclinicnotifysystem.notification;

import com.everythingcanbe.linebotclinicnotifysystem.provider.RoomStatus;

/**
 * 交易提交後才送出的推播。
 */
public record PendingPush(
        String lineUserId,
        NotificationType type,
        RoomStatus status,
        int targetNumber) {

    public int remaining() {
        return targetNumber - status.currentNumber();
    }

}
