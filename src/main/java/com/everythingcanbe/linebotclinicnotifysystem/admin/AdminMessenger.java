package com.everythingcanbe.linebotclinicnotifysystem.admin;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.linecorp.bot.messaging.client.MessagingApiClient;
import com.linecorp.bot.messaging.model.Message;
import com.linecorp.bot.messaging.model.PushMessageRequest;
import com.linecorp.bot.messaging.model.ReplyMessageRequest;
import com.linecorp.bot.messaging.model.ValidateMessageRequest;

/**
 * 後台 Channel 的訊息收發；失敗只記 log。
 */
public class AdminMessenger {

    private static final Logger log = LoggerFactory.getLogger(AdminMessenger.class);
    private static final long TIMEOUT_SECONDS = 10;

    private final MessagingApiClient client;
    private final AdminProperties properties;

    public AdminMessenger(MessagingApiClient client, AdminProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    public void reply(String replyToken, List<Message> messages) {
        if (messages.isEmpty()) {
            return;
        }
        try {
            client.replyMessage(new ReplyMessageRequest(replyToken, messages, false))
                    .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Failed to reply admin message", unwrap(e));
        }
    }

    /**
     * 推播給所有管理者（告警、每日摘要）。
     */
    public void pushToAdmins(List<Message> messages) {
        for (String adminId : properties.adminIds()) {
            try {
                client.pushMessage(UUID.randomUUID(), new PushMessageRequest(adminId, messages, false, null))
                        .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.error("Failed to push admin alert to {}", adminId, unwrap(e));
            }
        }
    }

    /**
     * 以 LINE 驗證 API 檢查訊息格式。
     *
     * @return 錯誤說明；格式正確時為 empty
     */
    public Optional<String> validate(List<Message> messages) {
        try {
            client.validateReply(new ValidateMessageRequest(messages)).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return Optional.empty();
        } catch (Exception e) {
            return Optional.of(String.valueOf(unwrap(e).getMessage()));
        }
    }

    private static Throwable unwrap(Exception e) {
        return e instanceof ExecutionException && e.getCause() != null ? e.getCause() : e;
    }

}
