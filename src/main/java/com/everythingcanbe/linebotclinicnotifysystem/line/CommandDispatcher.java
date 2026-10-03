package com.everythingcanbe.linebotclinicnotifysystem.line;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.linecorp.bot.messaging.model.Message;

import com.everythingcanbe.linebotclinicnotifysystem.monitor.RoomStatusQueryService;
import com.everythingcanbe.linebotclinicnotifysystem.provider.RoomNames;
import com.everythingcanbe.linebotclinicnotifysystem.provider.RoomStatus;
import com.everythingcanbe.linebotclinicnotifysystem.tracking.StartTrackingResult;
import com.everythingcanbe.linebotclinicnotifysystem.tracking.TrackingService;
import com.everythingcanbe.linebotclinicnotifysystem.tracking.TrackingView;

/**
 * 執行指令並產生回覆訊息。
 */
@Component
public class CommandDispatcher {

    static final int MAX_NUMBER = 999;

    static final String HELP_TEXT = """
            📋 支援的指令
            ・追蹤 2診 56號：開始追蹤（新的追蹤會取代舊的）
            ・目前狀態：查看追蹤進度與預估時間
            ・取消追蹤：停止追蹤
            ・診間：查看所有診間目前號碼
            ・設定門檻 10 5：自訂剩幾位時通知（到號一定會通知）
            ・查看門檻：查看目前通知門檻
            ・重設門檻：恢復預設門檻
            ・幫助：顯示本說明""";

    static final String NO_TRACKING_TEXT = "目前沒有追蹤中的號碼\n輸入「追蹤 2診 56號」開始追蹤";

    private final TrackingService trackingService;
    private final RoomStatusQueryService roomStatusQuery;
    private final MessageFactory messageFactory;

    public CommandDispatcher(TrackingService trackingService, RoomStatusQueryService roomStatusQuery,
            MessageFactory messageFactory) {
        this.trackingService = trackingService;
        this.roomStatusQuery = roomStatusQuery;
        this.messageFactory = messageFactory;
    }

    public List<Message> dispatch(String lineUserId, Command command) {
        Message reply = switch (command) {
            case Command.Track track -> track(lineUserId, track);
            case Command.Status ignored -> status(lineUserId);
            case Command.Cancel ignored -> text(trackingService.cancel(lineUserId) ? "已取消追蹤" : NO_TRACKING_TEXT);
            case Command.Rooms ignored -> rooms();
            case Command.SetThresholds set -> setThresholds(lineUserId, set.thresholds());
            case Command.ShowThresholds ignored -> text("目前通知門檻："
                    + MessageFactory.formatThresholds(trackingService.thresholds(lineUserId))
                    + "\n（自訂請輸入：設定門檻 10 5）");
            case Command.ResetThresholds ignored -> text("已恢復預設門檻："
                    + MessageFactory.formatThresholds(trackingService.resetThresholds(lineUserId)));
            case Command.Help ignored -> text(HELP_TEXT);
            case Command.Invalid invalid -> text(invalid.message());
            case Command.Unknown ignored -> text("看不懂這個指令 🙏\n輸入「幫助」查看所有指令");
        };
        return List.of(reply);
    }

    private Message track(String lineUserId, Command.Track track) {
        if (!roomStatusQuery.isConfiguredRoom(track.roomId())) {
            String rooms = roomStatusQuery.configuredRooms().stream()
                    .map(RoomNames::of)
                    .collect(Collectors.joining("、"));
            return text("目前只支援：" + rooms);
        }
        if (track.number() < 1 || track.number() > MAX_NUMBER) {
            return text("號碼需介於 1～" + MAX_NUMBER);
        }
        String roomName = RoomNames.of(track.roomId());
        StartTrackingResult result = trackingService.start(lineUserId, track.roomId(), track.number());
        return switch (result) {
            case StartTrackingResult.Started started -> text(startedText(started));
            case StartTrackingResult.RoomUnavailable ignored -> text("暫時無法取得" + roomName + "資料，請稍後再試");
            case StartTrackingResult.NotInSession ignored -> text(roomName + "目前未看診，無法追蹤");
            case StartTrackingResult.NumberReached reached -> {
                int current = reached.status().currentNumber();
                yield text(current == reached.targetNumber()
                        ? roomName + "目前已輪到 " + current + " 號，請立即報到"
                        : roomName + "目前 " + current + " 號，" + reached.targetNumber() + " 號已過號");
            }
        };
    }

    private static String startedText(StartTrackingResult.Started started) {
        StringBuilder text = new StringBuilder()
                .append("✅ 已開始追蹤\n")
                .append("診別：").append(started.status().roomName()).append('\n')
                .append("號碼：").append(started.targetNumber()).append('\n')
                .append("目前：").append(started.status().currentNumber()).append("號（剩餘 ")
                .append(started.remaining()).append(" 位）\n")
                .append("預估：").append(MessageFactory.formatEta(started.etaMinutes())).append('\n')
                .append("通知門檻：").append(MessageFactory.formatThresholds(started.thresholds()));
        if (started.replaced()) {
            text.append("\n（已取代先前的追蹤）");
        }
        return text.toString();
    }

    private Message status(String lineUserId) {
        Optional<TrackingView> tracking = trackingService.currentTracking(lineUserId);
        if (tracking.isEmpty()) {
            return text(NO_TRACKING_TEXT);
        }
        TrackingView view = tracking.get();
        String roomName = RoomNames.of(view.roomId());
        if (view.status().isEmpty()) {
            return text("暫時無法取得" + roomName + "資料，請稍後再試\n您的號碼：" + view.targetNumber() + "號");
        }
        RoomStatus status = view.status().get();
        if (!status.inSession()) {
            return text(roomName + "目前未看診\n您的號碼：" + view.targetNumber() + "號");
        }
        return messageFactory.trackingStatus(status, view.targetNumber(), view.etaMinutes());
    }

    private Message rooms() {
        String text = roomStatusQuery.configuredRooms().stream()
                .map(roomId -> roomLine(roomId, roomStatusQuery.current(roomId)))
                .collect(Collectors.joining("\n"));
        return text(text);
    }

    private static String roomLine(int roomId, Optional<RoomStatus> status) {
        if (status.isEmpty()) {
            return RoomNames.of(roomId) + "：暫時無法取得";
        }
        RoomStatus s = status.get();
        String info = s.inSession()
                ? String.join(" ", nonNull(s.department(), s.doctorName()))
                : String.join(" ", nonNull(s.department()));
        String label = info.isEmpty() ? s.roomName() : s.roomName() + "（" + info + "）";
        return label + "：" + (s.inSession() ? s.currentNumber() + "號" : "未看診");
    }

    private static List<String> nonNull(String... values) {
        return Arrays.stream(values).filter(v -> v != null && !v.isBlank()).toList();
    }

    private Message setThresholds(String lineUserId, List<Integer> thresholds) {
        try {
            List<Integer> updated = trackingService.updateThresholds(lineUserId, thresholds);
            return text("✅ 通知門檻已更新：" + MessageFactory.formatThresholds(updated));
        } catch (IllegalArgumentException e) {
            return text(e.getMessage() + "\n" + CommandParser.THRESHOLD_USAGE);
        }
    }

    private Message text(String text) {
        return messageFactory.text(text);
    }

}
