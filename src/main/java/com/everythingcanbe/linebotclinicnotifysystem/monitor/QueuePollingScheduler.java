package com.everythingcanbe.linebotclinicnotifysystem.monitor;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.everythingcanbe.linebotclinicnotifysystem.provider.ClinicProvider;
import com.everythingcanbe.linebotclinicnotifysystem.provider.RoomStatus;

@Component
public class QueuePollingScheduler {

    private static final Logger log = LoggerFactory.getLogger(QueuePollingScheduler.class);

    private final ClinicProvider provider;
    private final RoomStatusMonitor monitor;

    public QueuePollingScheduler(ClinicProvider provider, RoomStatusMonitor monitor) {
        this.provider = provider;
        this.monitor = monitor;
    }

    @Scheduled(fixedDelayString = "${clinic.polling.interval-seconds}", initialDelay = 3, timeUnit = TimeUnit.SECONDS)
    public void poll() {
        for (RoomStatus status : provider.fetchAllRooms()) {
            try {
                monitor.onStatus(status);
            } catch (Exception e) {
                log.error("Failed to process status of room {}", status.roomId(), e);
            }
        }
    }

}
