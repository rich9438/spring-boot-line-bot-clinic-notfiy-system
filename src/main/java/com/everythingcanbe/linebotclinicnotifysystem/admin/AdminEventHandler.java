package com.everythingcanbe.linebotclinicnotifysystem.admin;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.linecorp.bot.messaging.model.Message;
import com.linecorp.bot.webhook.model.Event;
import com.linecorp.bot.webhook.model.FollowEvent;
import com.linecorp.bot.webhook.model.MessageEvent;
import com.linecorp.bot.webhook.model.PostbackEvent;
import com.linecorp.bot.webhook.model.ReplyEvent;
import com.linecorp.bot.webhook.model.TextMessageContent;
import com.linecorp.bot.webhook.model.UserSource;

import com.everythingcanbe.linebotclinicnotifysystem.admin.audit.AdminAuditLog;
import com.everythingcanbe.linebotclinicnotifysystem.admin.audit.AdminAuditLogRepository;

/**
 * 處理後台 Webhook 事件：白名單檢查 → 解析指令 → 稽核 → 執行 → Reply。只處理 1 對 1 聊天。
 */
@Component
@ConditionalOnAdminEnabled
public class AdminEventHandler {

    private static final Logger log = LoggerFactory.getLogger(AdminEventHandler.class);

    private final AdminProperties properties;
    private final AdminCommandParser parser;
    private final AdminCommandDispatcher dispatcher;
    private final AdminCardFactory cards;
    private final AdminMessenger messenger;
    private final AdminAuditLogRepository auditLogRepository;
    private final Clock clock;

    public AdminEventHandler(AdminProperties properties, AdminCommandParser parser, AdminCommandDispatcher dispatcher,
            AdminCardFactory cards, AdminMessenger messenger, AdminAuditLogRepository auditLogRepository,
            Clock clock) {
        this.properties = properties;
        this.parser = parser;
        this.dispatcher = dispatcher;
        this.cards = cards;
        this.messenger = messenger;
        this.auditLogRepository = auditLogRepository;
        this.clock = clock;
    }

    public void handle(Event event) {
        if (!(event.source() instanceof UserSource source) || !(event instanceof ReplyEvent replyEvent)) {
            return;
        }
        String userId = source.userId();
        try {
            List<Message> reply = switch (event) {
                case MessageEvent message when message.message() instanceof TextMessageContent text ->
                        execute(userId, text.text(), parser.parse(text.text()));
                case PostbackEvent postback ->
                        execute(userId, "[postback] " + postback.postback().data(),
                                parser.parsePostback(postback.postback().data()));
                case FollowEvent ignored -> List.of(properties.isAdmin(userId) ? cards.help()
                        : cards.unauthorized(userId));
                default -> List.of();
            };
            messenger.reply(replyEvent.replyToken(), reply);
        } catch (Exception e) {
            log.error("Failed to handle admin event from {}", userId, e);
            messenger.reply(replyEvent.replyToken(), List.of(cards.error("指令執行失敗", String.valueOf(e.getMessage()))));
        }
    }

    private List<Message> execute(String userId, String rawText, AdminCommand command) {
        if (!properties.isAdmin(userId)) {
            log.warn("Unauthorized admin command from {}", userId);
            return List.of(cards.unauthorized(userId));
        }
        log.info("Admin command from {}: {}", userId, command.getClass().getSimpleName());
        auditLogRepository.save(new AdminAuditLog(userId, rawText, LocalDateTime.now(clock)));
        return dispatcher.dispatch(userId, command);
    }

}
