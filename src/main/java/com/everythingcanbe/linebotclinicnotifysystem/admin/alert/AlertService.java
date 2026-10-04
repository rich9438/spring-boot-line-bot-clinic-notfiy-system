package com.everythingcanbe.linebotclinicnotifysystem.admin.alert;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jakarta.annotation.PreDestroy;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.linecorp.bot.messaging.model.Message;

import com.everythingcanbe.linebotclinicnotifysystem.admin.AdminCardFactory;
import com.everythingcanbe.linebotclinicnotifysystem.admin.AdminMessenger;
import com.everythingcanbe.linebotclinicnotifysystem.admin.AdminProperties;
import com.everythingcanbe.linebotclinicnotifysystem.admin.ConditionalOnAdminEnabled;
import com.everythingcanbe.linebotclinicnotifysystem.line.PushFailedEvent;
import com.everythingcanbe.linebotclinicnotifysystem.monitor.PollingHealth;
import com.everythingcanbe.linebotclinicnotifysystem.monitor.RoomFetchFailedEvent;
import com.everythingcanbe.linebotclinicnotifysystem.monitor.RoomFetchRecoveredEvent;

/**
 * 將系統事件轉為告警推播給所有管理者。推播在背景執行緒進行，不拖慢排程與前台推播。
 */
@Service
@ConditionalOnAdminEnabled
public class AlertService {

    private final AdminMessenger messenger;
    private final AdminCardFactory cards;
    private final AlertThrottle throttle;
    private final AdminProperties properties;
    private final PollingHealth pollingHealth;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public AlertService(AdminMessenger messenger, AdminCardFactory cards, AlertThrottle throttle,
            AdminProperties properties, PollingHealth pollingHealth) {
        this.messenger = messenger;
        this.cards = cards;
        this.throttle = throttle;
        this.properties = properties;
        this.pollingHealth = pollingHealth;
    }

    /** 只在連續失敗次數「剛好」達到門檻時告警一次，避免每輪重複 */
    @EventListener
    public void onRoomFetchFailed(RoomFetchFailedEvent event) {
        if (event.consecutiveFailures() == properties.alerts().fetchFailureThreshold()
                && throttle.tryAcquire("fetch-failed:" + event.roomId())) {
            send(cards.fetchFailed(event.roomId(), event.consecutiveFailures(),
                    pollingHealth.get(event.roomId()).lastSuccessAt()));
        }
    }

    @EventListener
    public void onRoomFetchRecovered(RoomFetchRecoveredEvent event) {
        if (event.failedCount() >= properties.alerts().fetchFailureThreshold()) {
            send(cards.fetchRecovered(event.roomId(), event.failedCount()));
        }
    }

    @EventListener
    public void onPushFailed(PushFailedEvent event) {
        if (throttle.tryAcquire("push-failed")) {
            send(cards.pushFailed(event.lineUserId(), event.error()));
        }
    }

    public void send(Message message) {
        executor.execute(() -> messenger.pushToAdmins(List.of(message)));
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }

}
