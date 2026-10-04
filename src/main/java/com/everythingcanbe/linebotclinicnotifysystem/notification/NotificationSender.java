package com.everythingcanbe.linebotclinicnotifysystem.notification;

/**
 * 推播通知的出口，與 LINE 實作解耦。
 */
public interface NotificationSender {

    /**
     * @return 是否成功送達
     */
    boolean send(PendingPush push);

}
