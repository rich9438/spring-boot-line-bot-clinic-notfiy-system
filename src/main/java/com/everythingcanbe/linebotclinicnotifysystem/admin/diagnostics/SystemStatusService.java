package com.everythingcanbe.linebotclinicnotifysystem.admin.diagnostics;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;

import com.everythingcanbe.linebotclinicnotifysystem.admin.ConditionalOnAdminEnabled;
import com.everythingcanbe.linebotclinicnotifysystem.config.ClinicProperties;
import com.everythingcanbe.linebotclinicnotifysystem.monitor.PollingHealth;
import com.everythingcanbe.linebotclinicnotifysystem.monitor.RoomStatusCache;
import com.everythingcanbe.linebotclinicnotifysystem.provider.RoomNames;
import com.everythingcanbe.linebotclinicnotifysystem.provider.RoomStatus;
import com.everythingcanbe.linebotclinicnotifysystem.repository.SubscriberRepository;
import com.everythingcanbe.linebotclinicnotifysystem.repository.TrackingJobRepository;

/**
 * 後台「狀態」指令：版本、運行時間、資料庫、各診抓取與追蹤狀況、前台推播額度。
 */
@Service
@ConditionalOnAdminEnabled
public class SystemStatusService {

    private final ObjectProvider<BuildProperties> buildProperties;
    private final ClinicProperties clinicProperties;
    private final PollingHealth pollingHealth;
    private final RoomStatusCache cache;
    private final SubscriberRepository subscriberRepository;
    private final TrackingJobRepository trackingJobRepository;
    private final ClinicChannelOperations clinicChannel;

    public SystemStatusService(ObjectProvider<BuildProperties> buildProperties, ClinicProperties clinicProperties,
            PollingHealth pollingHealth, RoomStatusCache cache, SubscriberRepository subscriberRepository,
            TrackingJobRepository trackingJobRepository, ClinicChannelOperations clinicChannel) {
        this.buildProperties = buildProperties;
        this.clinicProperties = clinicProperties;
        this.pollingHealth = pollingHealth;
        this.cache = cache;
        this.subscriberRepository = subscriberRepository;
        this.trackingJobRepository = trackingJobRepository;
        this.clinicChannel = clinicChannel;
    }

    public SystemStatus status() {
        boolean databaseUp;
        long followers = 0;
        Map<Integer, Long> activeByRoom = new HashMap<>();
        try {
            followers = subscriberRepository.countByFollowingTrue();
            for (Object[] row : trackingJobRepository.countActiveByRoom()) {
                activeByRoom.put((Integer) row[0], (Long) row[1]);
            }
            databaseUp = true;
        } catch (Exception e) {
            databaseUp = false;
        }
        List<RoomLine> rooms = clinicProperties.rooms().stream()
                .map(roomId -> {
                    PollingHealth.RoomHealth health = pollingHealth.get(roomId);
                    Optional<RoomStatus> status = cache.get(roomId);
                    return new RoomLine(RoomNames.of(roomId), status.map(RoomStatus::inSession).orElse(false),
                            status.map(RoomStatus::currentNumber).orElse(null), health.lastSuccessAt(),
                            health.consecutiveFailures(), activeByRoom.getOrDefault(roomId, 0L));
                })
                .toList();
        return new SystemStatus(version(), uptime(), databaseUp, followers, rooms, clinicChannel.quota());
    }

    public String version() {
        BuildProperties build = buildProperties.getIfAvailable();
        if (build == null) {
            return "dev";
        }
        Instant time = build.getTime();
        return time == null ? build.getVersion()
                : build.getVersion() + "（" + LocalDateTime.ofInstant(time, clinicProperties.zoneId())
                        .toString().replace('T', ' ').substring(0, 16) + " 建置）";
    }

    public static Duration uptime() {
        return Duration.ofMillis(ManagementFactory.getRuntimeMXBean().getUptime());
    }

    public record SystemStatus(String version, Duration uptime, boolean databaseUp, long followers,
            List<RoomLine> rooms, Optional<ClinicChannelOperations.Quota> quota) {

        public long activeTrackings() {
            return rooms.stream().mapToLong(RoomLine::activeTrackings).sum();
        }

    }

    /**
     * @param lastSuccessAt 最後成功抓取時間，尚未成功過為 null
     */
    public record RoomLine(String roomName, boolean inSession, Integer currentNumber, LocalDateTime lastSuccessAt,
            int consecutiveFailures, long activeTrackings) {
    }

}
