package com.everythingcanbe.linebotclinicnotifysystem.admin.alert;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.everythingcanbe.linebotclinicnotifysystem.admin.AdminCardFactory;
import com.everythingcanbe.linebotclinicnotifysystem.admin.ConditionalOnAdminEnabled;
import com.everythingcanbe.linebotclinicnotifysystem.admin.diagnostics.SystemStatusService;
import com.everythingcanbe.linebotclinicnotifysystem.repository.TrackingJobRepository;

/**
 * 服務啟動完成時通知管理者（部署完成或容器意外重啟）。
 */
@Component
@ConditionalOnAdminEnabled
public class StartupNotifier {

    private final AlertService alertService;
    private final AdminCardFactory cards;
    private final SystemStatusService statusService;
    private final TrackingJobRepository trackingJobRepository;

    public StartupNotifier(AlertService alertService, AdminCardFactory cards, SystemStatusService statusService,
            TrackingJobRepository trackingJobRepository) {
        this.alertService = alertService;
        this.cards = cards;
        this.statusService = statusService;
        this.trackingJobRepository = trackingJobRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        alertService.send(cards.startup(statusService.version(), trackingJobRepository.findByActiveTrue().size()));
    }

}
