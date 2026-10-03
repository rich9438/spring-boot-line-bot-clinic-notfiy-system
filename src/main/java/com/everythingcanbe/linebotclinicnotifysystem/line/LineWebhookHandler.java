package com.everythingcanbe.linebotclinicnotifysystem.line;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.linecorp.bot.messaging.model.Message;
import com.linecorp.bot.spring.boot.handler.annotation.EventMapping;
import com.linecorp.bot.spring.boot.handler.annotation.LineMessageHandler;
import com.linecorp.bot.webhook.model.Event;
import com.linecorp.bot.webhook.model.FollowEvent;
import com.linecorp.bot.webhook.model.MessageEvent;
import com.linecorp.bot.webhook.model.TextMessageContent;
import com.linecorp.bot.webhook.model.UnfollowEvent;
import com.linecorp.bot.webhook.model.UserSource;

import com.everythingcanbe.linebotclinicnotifysystem.tracking.SubscriberService;
import com.everythingcanbe.linebotclinicnotifysystem.tracking.TrackingService;

/**
 * LINE Webhook 事件處理（POST /callback，簽章由 SDK 驗證）。回傳的訊息會由 SDK 以 Reply API 送出。
 * 只處理 1 對 1 聊天，群組與多人聊天室訊息一律忽略。
 */
@LineMessageHandler
public class LineWebhookHandler {

    private static final Logger log = LoggerFactory.getLogger(LineWebhookHandler.class);

    private final CommandParser commandParser;
    private final CommandDispatcher commandDispatcher;
    private final SubscriberService subscriberService;
    private final TrackingService trackingService;
    private final LineMessenger messenger;
    private final MessageFactory messageFactory;

    public LineWebhookHandler(CommandParser commandParser, CommandDispatcher commandDispatcher,
            SubscriberService subscriberService, TrackingService trackingService, LineMessenger messenger,
            MessageFactory messageFactory) {
        this.commandParser = commandParser;
        this.commandDispatcher = commandDispatcher;
        this.subscriberService = subscriberService;
        this.trackingService = trackingService;
        this.messenger = messenger;
        this.messageFactory = messageFactory;
    }

    @EventMapping
    public List<Message> handleText(MessageEvent event, TextMessageContent content) {
        if (!(event.source() instanceof UserSource source)) {
            return List.of();
        }
        Command command = commandParser.parse(content.text());
        log.info("Command from {}: {}", source.userId(), command);
        return commandDispatcher.dispatch(source.userId(), command);
    }

    @EventMapping
    public List<Message> handleFollow(FollowEvent event) {
        if (!(event.source() instanceof UserSource source)) {
            return List.of();
        }
        String displayName = messenger.displayName(source.userId()).orElse(null);
        subscriberService.follow(source.userId(), displayName);
        String greeting = displayName == null ? "您好！" : displayName + " 您好！";
        return List.of(messageFactory.text(greeting + "我會在快輪到您看診時通知您。\n\n" + CommandDispatcher.HELP_TEXT));
    }

    @EventMapping
    public void handleUnfollow(UnfollowEvent event) {
        if (event.source() instanceof UserSource source) {
            trackingService.unfollow(source.userId());
        }
    }

    @EventMapping
    public void handleDefault(Event event) {
        log.debug("Ignored event: {}", event.getClass().getSimpleName());
    }

}
