package com.everythingcanbe.linebotclinicnotifysystem.monitor;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.everythingcanbe.linebotclinicnotifysystem.provider.RoomStatus;

/**
 * 各診資料抓取的健康狀態：最後成功時間、連續失敗次數。失敗與恢復時發布事件。
 */
@Component
public class PollingHealth {

    private final Map<Integer, RoomHealth> health = new ConcurrentHashMap<>();
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public PollingHealth(ApplicationEventPublisher eventPublisher, Clock clock) {
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /**
     * 記錄一輪抓取結果：設定的診間中沒有出現在 fetched 的視為失敗。
     */
    public void record(Collection<Integer> configuredRooms, List<RoomStatus> fetched) {
        Set<Integer> succeeded = fetched.stream().map(RoomStatus::roomId).collect(Collectors.toSet());
        LocalDateTime now = LocalDateTime.now(clock);
        for (Integer roomId : configuredRooms) {
            RoomHealth previous = health.getOrDefault(roomId, RoomHealth.INITIAL);
            if (succeeded.contains(roomId)) {
                health.put(roomId, new RoomHealth(now, 0));
                if (previous.consecutiveFailures() > 0) {
                    eventPublisher.publishEvent(new RoomFetchRecoveredEvent(roomId, previous.consecutiveFailures()));
                }
            } else {
                int failures = previous.consecutiveFailures() + 1;
                health.put(roomId, new RoomHealth(previous.lastSuccessAt(), failures));
                eventPublisher.publishEvent(new RoomFetchFailedEvent(roomId, failures));
            }
        }
    }

    public RoomHealth get(int roomId) {
        return health.getOrDefault(roomId, RoomHealth.INITIAL);
    }

    /**
     * @param lastSuccessAt 最後成功抓取時間，尚未成功過為 null
     */
    public record RoomHealth(LocalDateTime lastSuccessAt, int consecutiveFailures) {
        static final RoomHealth INITIAL = new RoomHealth(null, 0);
    }

}
