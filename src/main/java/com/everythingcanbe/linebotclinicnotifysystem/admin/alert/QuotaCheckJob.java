package com.everythingcanbe.linebotclinicnotifysystem.admin.alert;

import java.time.Clock;
import java.time.YearMonth;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.everythingcanbe.linebotclinicnotifysystem.admin.AdminCardFactory;
import com.everythingcanbe.linebotclinicnotifysystem.admin.AdminProperties;
import com.everythingcanbe.linebotclinicnotifysystem.admin.ConditionalOnAdminEnabled;
import com.everythingcanbe.linebotclinicnotifysystem.admin.diagnostics.ClinicChannelOperations;

/**
 * 每小時檢查前台推播額度，達預警比例與 100% 時各通知一次（每月重置；應用程式重啟後可能再通知一次）。
 */
@Component
@ConditionalOnAdminEnabled
public class QuotaCheckJob {

    private final ClinicChannelOperations clinicChannel;
    private final AlertService alertService;
    private final AdminCardFactory cards;
    private final AdminProperties properties;
    private final Clock clock;
    private final Set<String> notified = ConcurrentHashMap.newKeySet();

    public QuotaCheckJob(ClinicChannelOperations clinicChannel, AlertService alertService, AdminCardFactory cards,
            AdminProperties properties, Clock clock) {
        this.clinicChannel = clinicChannel;
        this.alertService = alertService;
        this.cards = cards;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(cron = "0 5 * * * *", zone = "${clinic.zone-id:Asia/Taipei}")
    public void check() {
        clinicChannel.quota().ifPresent(quota -> {
            Double ratio = quota.ratio();
            if (ratio == null) {
                return;
            }
            String month = YearMonth.now(clock).toString();
            String level = ratio >= 1 ? "100" : ratio >= properties.alerts().quotaWarnRatio() ? "warn" : null;
            if (level != null && notified.add(month + ":" + level)) {
                alertService.send(cards.quotaWarning(quota));
            }
        });
    }

}
