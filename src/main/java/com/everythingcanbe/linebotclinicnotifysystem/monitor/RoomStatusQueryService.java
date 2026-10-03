package com.everythingcanbe.linebotclinicnotifysystem.monitor;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.everythingcanbe.linebotclinicnotifysystem.config.ClinicProperties;
import com.everythingcanbe.linebotclinicnotifysystem.provider.ClinicProvider;
import com.everythingcanbe.linebotclinicnotifysystem.provider.RoomStatus;

/**
 * 供指令查詢使用的診間狀態：優先讀快取，快取尚未建立（剛啟動）時直接向 Provider 查詢。
 */
@Service
public class RoomStatusQueryService {

    private static final Logger log = LoggerFactory.getLogger(RoomStatusQueryService.class);

    private final RoomStatusCache cache;
    private final ClinicProvider provider;
    private final ClinicProperties properties;

    public RoomStatusQueryService(RoomStatusCache cache, ClinicProvider provider, ClinicProperties properties) {
        this.cache = cache;
        this.provider = provider;
        this.properties = properties;
    }

    public String providerCode() {
        return provider.code();
    }

    public boolean isConfiguredRoom(int roomId) {
        return properties.rooms().contains(roomId);
    }

    public List<Integer> configuredRooms() {
        return properties.rooms();
    }

    public Optional<RoomStatus> current(int roomId) {
        Optional<RoomStatus> cached = cache.get(roomId);
        if (cached.isPresent()) {
            return cached;
        }
        try {
            return Optional.of(provider.fetchRoom(roomId));
        } catch (Exception e) {
            log.warn("Failed to fetch room {} on demand: {}", roomId, e.toString());
            return Optional.empty();
        }
    }

}
