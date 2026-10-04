package com.everythingcanbe.linebotclinicnotifysystem.line;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * 記錄「已選診間、等待輸入號碼」的使用者。屬於短暫的對話狀態，存於記憶體，逾時自動失效。
 */
@Component
public class PendingTrackStore {

    static final Duration TTL = Duration.ofMinutes(5);

    private final Map<String, Pending> pendings = new ConcurrentHashMap<>();
    private final Clock clock;

    public PendingTrackStore(Clock clock) {
        this.clock = clock;
    }

    public void put(String lineUserId, int roomId) {
        Instant now = clock.instant();
        pendings.values().removeIf(pending -> pending.expiresAt().isBefore(now));
        pendings.put(lineUserId, new Pending(roomId, now.plus(TTL)));
    }

    /**
     * 取出並移除等待中的診間；已逾時則視為不存在。
     */
    public Optional<Integer> take(String lineUserId) {
        Pending pending = pendings.remove(lineUserId);
        if (pending == null || pending.expiresAt().isBefore(clock.instant())) {
            return Optional.empty();
        }
        return Optional.of(pending.roomId());
    }

    public void clear(String lineUserId) {
        pendings.remove(lineUserId);
    }

    private record Pending(int roomId, Instant expiresAt) {
    }

}
