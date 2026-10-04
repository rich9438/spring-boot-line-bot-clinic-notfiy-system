package com.everythingcanbe.linebotclinicnotifysystem.monitor;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.everythingcanbe.linebotclinicnotifysystem.config.ClinicProperties;
import com.everythingcanbe.linebotclinicnotifysystem.provider.ClinicProvider;
import com.everythingcanbe.linebotclinicnotifysystem.provider.RoomStatus;

@Component
public class QueuePollingScheduler {

    private static final Logger log = LoggerFactory.getLogger(QueuePollingScheduler.class);

    private final ClinicProvider provider;
    private final RoomStatusMonitor monitor;
    private final PollingHealth pollingHealth;
    private final ClinicProperties properties;

    public QueuePollingScheduler(ClinicProvider provider, RoomStatusMonitor monitor, PollingHealth pollingHealth,
            ClinicProperties properties) {
        this.provider = provider;
        this.monitor = monitor;
        this.pollingHealth = pollingHealth;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${clinic.polling.interval-seconds}", initialDelay = 3, timeUnit = TimeUnit.SECONDS)
    public void scheduledPoll() {
        poll();
    }

    /**
     * 執行一輪抓取（排程與後台「抓取」指令共用）。
     *
     * @return 成功抓取的診間狀態
     */
    public synchronized List<RoomStatus> poll() {
        List<RoomStatus> statuses = provider.fetchAllRooms();
        pollingHealth.record(properties.rooms(), statuses);
        for (RoomStatus status : statuses) {
            try {
                monitor.onStatus(status);
            } catch (Exception e) {
                log.error("Failed to process status of room {}", status.roomId(), e);
            }
        }
        return statuses;
    }

}
