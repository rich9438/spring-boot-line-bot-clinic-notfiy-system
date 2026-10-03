package com.everythingcanbe.linebotclinicnotifysystem.monitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.everythingcanbe.linebotclinicnotifysystem.tracking.TrackingService;

/**
 * 每日結束所有殘留的追蹤任務，避免隔天號碼重置後誤發通知。
 */
@Component
public class DailyCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(DailyCleanupJob.class);

    private final TrackingService trackingService;

    public DailyCleanupJob(TrackingService trackingService) {
        this.trackingService = trackingService;
    }

    @Scheduled(cron = "0 59 23 * * *", zone = "${clinic.zone-id:Asia/Taipei}")
    public void expireTrackingJobs() {
        int expired = trackingService.expireAll();
        log.info("Daily cleanup expired {} tracking job(s)", expired);
    }

}
