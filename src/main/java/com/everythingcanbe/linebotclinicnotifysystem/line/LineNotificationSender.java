package com.everythingcanbe.linebotclinicnotifysystem.line;

import java.util.List;

import org.springframework.stereotype.Component;

import com.linecorp.bot.messaging.model.Message;

import com.everythingcanbe.linebotclinicnotifysystem.eta.WaitTimeEstimator;
import com.everythingcanbe.linebotclinicnotifysystem.notification.NotificationSender;
import com.everythingcanbe.linebotclinicnotifysystem.notification.PendingPush;

@Component
public class LineNotificationSender implements NotificationSender {

    private final LineMessenger messenger;
    private final MessageFactory messageFactory;
    private final WaitTimeEstimator waitTimeEstimator;

    public LineNotificationSender(LineMessenger messenger, MessageFactory messageFactory,
            WaitTimeEstimator waitTimeEstimator) {
        this.messenger = messenger;
        this.messageFactory = messageFactory;
        this.waitTimeEstimator = waitTimeEstimator;
    }

    @Override
    public boolean send(PendingPush push) {
        Message message = switch (push.type()) {
            case PROGRESS -> messageFactory.progress(push.status(), push.targetNumber(),
                    waitTimeEstimator.estimateMinutes(push.status().providerCode(), push.status().roomId(),
                            push.remaining()));
            case ARRIVED -> messageFactory.arrived(push.status(), push.targetNumber());
            case MISSED -> messageFactory.missed(push.status(), push.targetNumber());
            case SESSION_RESET -> messageFactory.sessionReset(push.status(), push.targetNumber());
        };
        return messenger.push(push.lineUserId(), List.of(message));
    }

}
