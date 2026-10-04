package com.everythingcanbe.linebotclinicnotifysystem.admin.alert;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.everythingcanbe.linebotclinicnotifysystem.admin.AdminCardFactory;
import com.everythingcanbe.linebotclinicnotifysystem.admin.ConditionalOnAdminEnabled;
import com.everythingcanbe.linebotclinicnotifysystem.admin.diagnostics.DailySummaryService;

@Component
@ConditionalOnAdminEnabled
public class DailySummaryJob {

    private final DailySummaryService summaryService;
    private final AlertService alertService;
    private final AdminCardFactory cards;
    private final Clock clock;

    public DailySummaryJob(DailySummaryService summaryService, AlertService alertService, AdminCardFactory cards,
            Clock clock) {
        this.summaryService = summaryService;
        this.alertService = alertService;
        this.cards = cards;
        this.clock = clock;
    }

    @Scheduled(cron = "${admin.alerts.daily-summary-cron:0 0 22 * * *}", zone = "${clinic.zone-id:Asia/Taipei}")
    public void sendDailySummary() {
        alertService.send(cards.dailySummary(summaryService.summarize(LocalDate.now(clock))));
    }

}
