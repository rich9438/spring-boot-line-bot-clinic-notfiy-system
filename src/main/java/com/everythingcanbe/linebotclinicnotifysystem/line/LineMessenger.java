package com.everythingcanbe.linebotclinicnotifysystem.line;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.linecorp.bot.messaging.client.MessagingApiClient;
import com.linecorp.bot.messaging.model.Message;
import com.linecorp.bot.messaging.model.PushMessageRequest;

/**
 * 包裝 LINE Messaging API 呼叫；失敗只記 log，不中斷呼叫端流程。
 */
@Component
public class LineMessenger {

    private static final Logger log = LoggerFactory.getLogger(LineMessenger.class);
    private static final long TIMEOUT_SECONDS = 10;

    private final MessagingApiClient client;
    private final ApplicationEventPublisher eventPublisher;

    public LineMessenger(MessagingApiClient client, ApplicationEventPublisher eventPublisher) {
        this.client = client;
        this.eventPublisher = eventPublisher;
    }

    public boolean push(String lineUserId, List<Message> messages) {
        try {
            client.pushMessage(UUID.randomUUID(), new PushMessageRequest(lineUserId, messages, false, null))
                    .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            log.error("Failed to push message to {}", lineUserId, e);
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            eventPublisher.publishEvent(new PushFailedEvent(lineUserId, cause.toString()));
            return false;
        }
    }

    public Optional<String> displayName(String lineUserId) {
        try {
            return Optional.ofNullable(client.getProfile(lineUserId).get(TIMEOUT_SECONDS, TimeUnit.SECONDS).body())
                    .map(profile -> profile.displayName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Failed to get profile of {}: {}", lineUserId, e.toString());
            return Optional.empty();
        }
    }

}
