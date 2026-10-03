package com.everythingcanbe.linebotclinicnotifysystem.tracking;

import java.util.List;
import java.util.Optional;

import com.everythingcanbe.linebotclinicnotifysystem.provider.RoomStatus;

public sealed interface StartTrackingResult {

    record Started(RoomStatus status, int targetNumber, List<Integer> thresholds, Optional<Long> etaMinutes,
            boolean replaced) implements StartTrackingResult {

        public int remaining() {
            return targetNumber - status.currentNumber();
        }

    }

    /** 診間狀態暫時無法取得 */
    record RoomUnavailable(int roomId) implements StartTrackingResult {
    }

    record NotInSession(RoomStatus status) implements StartTrackingResult {
    }

    /** 號碼已到或已過 */
    record NumberReached(RoomStatus status, int targetNumber) implements StartTrackingResult {
    }

}
