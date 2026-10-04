package com.everythingcanbe.linebotclinicnotifysystem.line;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.linecorp.bot.messaging.model.Message;

import com.everythingcanbe.linebotclinicnotifysystem.config.ClinicProperties;
import com.everythingcanbe.linebotclinicnotifysystem.config.NotificationProperties;
import com.everythingcanbe.linebotclinicnotifysystem.line.FlexParts.Tone;
import com.everythingcanbe.linebotclinicnotifysystem.provider.RoomStatus;
import com.everythingcanbe.linebotclinicnotifysystem.tracking.StartTrackingResult;

/**
 * 以範例資料產生所有卡片，供測試與匯出 request body（deploy/flex-samples）使用。
 */
final class FlexSamples {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 5, 14, 20);

    static final MessageFactory FACTORY = new MessageFactory(
            new ClinicProperties("wuobs", "慈心吳婦產科", ZoneId.of("Asia/Taipei"), null, null, List.of(1, 2, 3)),
            new NotificationProperties(List.of(10, 3, 0), 10, new NotificationProperties.Eta(60, 3)));

    private FlexSamples() {
    }

    static RoomStatus room(int roomId, Integer number) {
        String department = roomId == 3 ? "小兒科" : "婦產科";
        boolean inSession = number != null;
        return new RoomStatus("wuobs", roomId, new String[] {"", "一診", "二診", "三診"}[roomId],
                inSession ? "吳瑞聰" : null, department, number, inSession, NOW);
    }

    /**
     * @return 檔名 → 訊息（一則回覆可含多則訊息）
     */
    static Map<String, List<Message>> all() {
        List<MessageFactory.RoomOption> rooms = List.of(
                new MessageFactory.RoomOption(1, Optional.of(room(1, 35))),
                new MessageFactory.RoomOption(2, Optional.of(room(2, 46))),
                new MessageFactory.RoomOption(3, Optional.of(room(3, null))));
        List<MessageFactory.RoomOption> closedRooms = List.of(
                new MessageFactory.RoomOption(1, Optional.of(room(1, null))),
                new MessageFactory.RoomOption(2, Optional.of(room(2, null))),
                new MessageFactory.RoomOption(3, Optional.of(room(3, null))));

        Map<String, List<Message>> samples = new LinkedHashMap<>();
        samples.put("01-tracking-started", List.of(FACTORY.trackingStarted(new StartTrackingResult.Started(
                room(2, 46), 56, List.of(10, 3, 0), Optional.of(20L), false))));
        samples.put("02-choose-room", List.of(FACTORY.rooms(rooms, "請選擇要追蹤的診間")));
        samples.put("03-ask-number", List.of(FACTORY.askNumber(room(2, 46))));
        samples.put("04-progress", List.of(FACTORY.progress(room(2, 53), 56, Optional.of(8L))));
        samples.put("05-arrived", List.of(FACTORY.arrived(room(2, 56), 56)));
        samples.put("06-missed", List.of(FACTORY.missed(room(2, 58), 56)));
        samples.put("07-session-reset", List.of(FACTORY.sessionReset(room(2, 1), 56)));
        samples.put("08-status", List.of(FACTORY.trackingStatus(room(2, 46), 56, Optional.of(20L))));
        samples.put("09-status-not-in-session", List.of(
                FACTORY.trackingStatusUnavailable(2, Optional.of(room(2, null)), 56)));
        samples.put("10-no-tracking", List.of(FACTORY.noTracking()));
        samples.put("11-rooms", List.of(FACTORY.rooms(rooms, "診間看診進度")));
        samples.put("12-rooms-all-closed", List.of(
                FACTORY.notice(Tone.NEUTRAL, "目前沒有看診中的診間", "請於看診時段再試。"),
                FACTORY.rooms(closedRooms, "診間看診進度")));
        samples.put("13-thresholds", List.of(FACTORY.thresholds("通知門檻", List.of(15, 8, 3, 0))));
        samples.put("14-cancelled", List.of(FACTORY.cancelled()));
        samples.put("15-number-passed", List.of(FACTORY.numberReached(room(2, 60), 56)));
        samples.put("16-help", List.of(FACTORY.help()));
        samples.put("17-welcome", List.of(FACTORY.welcome("小明")));
        samples.put("18-unknown-command", List.of(FACTORY.unknownCommand()));
        return samples;
    }

}
