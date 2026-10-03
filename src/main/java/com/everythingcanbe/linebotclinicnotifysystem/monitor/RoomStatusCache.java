package com.everythingcanbe.linebotclinicnotifysystem.monitor;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.everythingcanbe.linebotclinicnotifysystem.provider.RoomStatus;

/**
 * 記憶體內的診間狀態快取（未來可替換為 Redis）。
 */
@Component
public class RoomStatusCache {

    private final Map<Integer, RoomStatus> statuses = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> lastInSessionNumbers = new ConcurrentHashMap<>();

    public Optional<RoomStatus> get(int roomId) {
        return Optional.ofNullable(statuses.get(roomId));
    }

    public List<RoomStatus> all() {
        return statuses.values().stream()
                .sorted(Comparator.comparing(RoomStatus::roomId))
                .toList();
    }

    /**
     * 更新狀態並回傳前一筆狀態（首次為 null）。看診中的號碼另外保存，供診次重置判斷使用。
     */
    public RoomStatus put(RoomStatus status) {
        if (status.inSession()) {
            lastInSessionNumbers.put(status.roomId(), status.currentNumber());
        }
        return statuses.put(status.roomId(), status);
    }

    /**
     * 最後一次看診中的號碼；診間暫停（例如午休）期間仍保留，用於判斷下一個診次是否重新叫號。
     */
    public Optional<Integer> lastInSessionNumber(int roomId) {
        return Optional.ofNullable(lastInSessionNumbers.get(roomId));
    }

    public void clear() {
        statuses.clear();
        lastInSessionNumbers.clear();
    }

}
