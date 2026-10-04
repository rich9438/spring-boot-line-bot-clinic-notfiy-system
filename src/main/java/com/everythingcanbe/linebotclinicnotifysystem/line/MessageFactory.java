package com.everythingcanbe.linebotclinicnotifysystem.line;

import static com.everythingcanbe.linebotclinicnotifysystem.line.FlexParts.bigCenter;
import static com.everythingcanbe.linebotclinicnotifysystem.line.FlexParts.bubble;
import static com.everythingcanbe.linebotclinicnotifysystem.line.FlexParts.chips;
import static com.everythingcanbe.linebotclinicnotifysystem.line.FlexParts.commandRow;
import static com.everythingcanbe.linebotclinicnotifysystem.line.FlexParts.fillInButton;
import static com.everythingcanbe.linebotclinicnotifysystem.line.FlexParts.message;
import static com.everythingcanbe.linebotclinicnotifysystem.line.FlexParts.messageButton;
import static com.everythingcanbe.linebotclinicnotifysystem.line.FlexParts.note;
import static com.everythingcanbe.linebotclinicnotifysystem.line.FlexParts.numberPanel;
import static com.everythingcanbe.linebotclinicnotifysystem.line.FlexParts.paragraph;
import static com.everythingcanbe.linebotclinicnotifysystem.line.FlexParts.postbackButton;
import static com.everythingcanbe.linebotclinicnotifysystem.line.FlexParts.roomTitle;
import static com.everythingcanbe.linebotclinicnotifysystem.line.FlexParts.row;
import static com.everythingcanbe.linebotclinicnotifysystem.line.FlexParts.separator;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.linecorp.bot.messaging.model.FlexBubble;
import com.linecorp.bot.messaging.model.FlexCarousel;
import com.linecorp.bot.messaging.model.FlexComponent;
import com.linecorp.bot.messaging.model.Message;

import com.everythingcanbe.linebotclinicnotifysystem.config.ClinicProperties;
import com.everythingcanbe.linebotclinicnotifysystem.config.NotificationProperties;
import com.everythingcanbe.linebotclinicnotifysystem.line.FlexParts.Tone;
import com.everythingcanbe.linebotclinicnotifysystem.notification.ThresholdResolver;
import com.everythingcanbe.linebotclinicnotifysystem.provider.RoomNames;
import com.everythingcanbe.linebotclinicnotifysystem.provider.RoomStatus;
import com.everythingcanbe.linebotclinicnotifysystem.tracking.StartTrackingResult;
import com.everythingcanbe.linebotclinicnotifysystem.tracking.ThresholdSettings;

/**
 * 所有回覆與推播訊息，一律以 Flex Message 卡片呈現。altText 會顯示在通知與聊天列表，需能單獨閱讀。
 */
@Component
public class MessageFactory {

    private static final DateTimeFormatter UPDATE_TIME = DateTimeFormatter.ofPattern("MM/dd HH:mm");

    private final ClinicProperties clinicProperties;
    private final NotificationProperties notificationProperties;

    public MessageFactory(ClinicProperties clinicProperties, NotificationProperties notificationProperties) {
        this.clinicProperties = clinicProperties;
        this.notificationProperties = notificationProperties;
    }

    // ── 追蹤 ──────────────────────────────────────────────

    public Message trackingStarted(StartTrackingResult.Started started) {
        RoomStatus status = started.status();
        List<FlexComponent> body = new ArrayList<>(List.of(
                roomTitle(status.roomName(), info(status)),
                numberPanel(String.valueOf(status.currentNumber()), Tone.INFO, started.targetNumber()),
                row("剩餘", started.remaining() + " 位"),
                row("預估", formatEta(started.etaMinutes())),
                row("通知", formatThresholds(started.thresholds()))));
        if (started.replaced()) {
            body.add(note("已取代先前的追蹤"));
        }
        String altText = "✅ 已開始追蹤：%s %d 號，目前 %d 號（剩餘 %d 位）"
                .formatted(status.roomName(), started.targetNumber(), status.currentNumber(), started.remaining());
        return message(altText, bubble(Tone.INFO, "✅ 已開始追蹤", clinicName(), body, List.of(
                messageButton("目前狀態", "目前狀態", false, Tone.INFO),
                messageButton("取消追蹤", "取消追蹤", false, Tone.INFO))));
    }

    /** 已選定診間，請使用者輸入號碼 */
    public Message askNumber(RoomStatus status) {
        List<FlexComponent> body = List.of(
                roomTitle(status.roomName(), info(status)),
                bigCenter("目前叫號", String.valueOf(status.currentNumber()), Tone.INFO.color),
                paragraph("請直接輸入您的看診號碼，例如：56"),
                note("5 分鐘內有效"));
        String altText = "%s目前 %d 號，請輸入您的看診號碼".formatted(status.roomName(), status.currentNumber());
        return message(altText, bubble(Tone.INFO, "請輸入看診號碼", clinicName(), body, List.of()));
    }

    /** 建立追蹤時號碼已到或已過 */
    public Message numberReached(RoomStatus status, int targetNumber) {
        int current = status.currentNumber();
        if (current == targetNumber) {
            return notice(Tone.ARRIVED, "已輪到您了", status.roomName() + "目前已輪到 " + current + " 號，請立即報到。",
                    null);
        }
        return notice(Tone.ALERT, "此號碼已過號",
                status.roomName() + "目前 " + current + " 號，" + targetNumber + " 號已過號，請洽櫃台。", null);
    }

    // ── 推播 ──────────────────────────────────────────────

    public Message progress(RoomStatus status, int targetNumber, Optional<Long> etaMinutes) {
        int remaining = targetNumber - status.currentNumber();
        List<FlexComponent> body = List.of(
                roomTitle(status.roomName(), info(status)),
                numberPanel(String.valueOf(status.currentNumber()), Tone.PROGRESS, targetNumber),
                row("剩餘", remaining + " 位", Tone.PROGRESS.color),
                row("預估", formatEta(etaMinutes)),
                note("請準備前往診間候診"));
        String altText = "⏰ 即將輪到您：%s 目前 %d 號，您是 %d 號，剩餘 %d 位"
                .formatted(status.roomName(), status.currentNumber(), targetNumber, remaining);
        return message(altText, bubble(Tone.PROGRESS, "⏰ 即將輪到您看診", clinicName(), body, List.of(
                messageButton("目前狀態", "目前狀態", true, Tone.PROGRESS))));
    }

    public Message arrived(RoomStatus status, int targetNumber) {
        List<FlexComponent> body = List.of(
                roomTitle(status.roomName(), info(status)),
                bigCenter("已輪到", targetNumber + " 號", Tone.ARRIVED.color),
                paragraph("請立即前往" + status.roomName() + "報到。"),
                note("追蹤已自動結束"));
        String altText = "🔔 請立即報到：%s 已輪到 %d 號".formatted(status.roomName(), targetNumber);
        return message(altText, bubble(Tone.ARRIVED, "🔔 請立即報到", clinicName(), body, List.of()));
    }

    public Message missed(RoomStatus status, int targetNumber) {
        List<FlexComponent> body = List.of(
                roomTitle(status.roomName(), info(status)),
                numberPanel(String.valueOf(status.currentNumber()), Tone.ALERT, targetNumber),
                paragraph("您的號碼已過號，請洽櫃台。"),
                note("追蹤已結束"));
        String altText = "⚠️ 已過號：%s 目前 %d 號，您的 %d 號已過號，請洽櫃台"
                .formatted(status.roomName(), status.currentNumber(), targetNumber);
        return message(altText, bubble(Tone.ALERT, "⚠️ 已過號", clinicName(), body, List.of(
                messageButton("重新追蹤", "追蹤", true, Tone.ALERT))));
    }

    public Message sessionReset(RoomStatus status, int targetNumber) {
        List<FlexComponent> body = List.of(
                roomTitle(status.roomName(), info(status)),
                numberPanel(String.valueOf(status.currentNumber()), Tone.ALERT, targetNumber),
                paragraph("看診號碼已重新開始，原本的追蹤已結束。"),
                note("如需繼續，請重新追蹤"));
        String altText = "⚠️ 追蹤已結束：%s 看診號碼已重新開始（%d 號的追蹤已結束）"
                .formatted(status.roomName(), targetNumber);
        return message(altText, bubble(Tone.ALERT, "⚠️ 追蹤已結束", clinicName(), body, List.of(
                messageButton("重新追蹤", "追蹤", true, Tone.ALERT))));
    }

    // ── 查詢狀態 ──────────────────────────────────────────

    public Message trackingStatus(RoomStatus status, int targetNumber, Optional<Long> etaMinutes) {
        int remaining = Math.max(targetNumber - status.currentNumber(), 0);
        List<FlexComponent> body = new ArrayList<>(List.of(
                roomTitle(status.roomName(), info(status)),
                numberPanel(String.valueOf(status.currentNumber()), Tone.INFO, targetNumber),
                row("剩餘", remaining + " 位"),
                row("預估", formatEta(etaMinutes))));
        updateTimeNote(status).ifPresent(body::add);
        String altText = "%s 目前 %d 號，您是 %d 號，剩餘 %d 位"
                .formatted(status.roomName(), status.currentNumber(), targetNumber, remaining);
        return message(altText, bubble(Tone.INFO, "看診進度", clinicName(), body, List.of(
                messageButton("重新整理", "目前狀態", false, Tone.INFO),
                messageButton("取消追蹤", "取消追蹤", false, Tone.INFO))));
    }

    /** 追蹤中，但診間目前未看診或資料暫時無法取得 */
    public Message trackingStatusUnavailable(int roomId, Optional<RoomStatus> status, int targetNumber) {
        String roomName = RoomNames.of(roomId);
        boolean fetched = status.isPresent();
        List<FlexComponent> body = List.of(
                roomTitle(roomName, status.map(MessageFactory::info).orElse(null)),
                numberPanel(fetched ? "未看診" : "－", Tone.NEUTRAL, targetNumber),
                note(fetched ? "開始看診後，達到通知門檻時會自動通知您" : "暫時無法取得診間資料，請稍後再試"));
        String altText = fetched
                ? "%s目前未看診，您的號碼：%d 號".formatted(roomName, targetNumber)
                : "暫時無法取得%s資料，您的號碼：%d 號".formatted(roomName, targetNumber);
        return message(altText, bubble(Tone.NEUTRAL, "看診進度", clinicName(), body, List.of(
                messageButton("重新整理", "目前狀態", false, Tone.NEUTRAL),
                messageButton("取消追蹤", "取消追蹤", false, Tone.NEUTRAL))));
    }

    public Message noTracking() {
        return notice(Tone.NEUTRAL, "目前沒有追蹤", "點選下方按鈕選擇診間，或輸入「追蹤 2診 56號」開始追蹤。",
                messageButton("開始追蹤", "追蹤", true, Tone.INFO));
    }

    // ── 診間 ──────────────────────────────────────────────

    /**
     * 所有診間：一個 Carousel、每個診間一張卡片。看診中的診間附「追蹤」按鈕（點選後自動開啟鍵盤以輸入號碼）。
     */
    public Message rooms(List<RoomOption> rooms, String altTextPrefix) {
        List<FlexBubble> bubbles = rooms.stream().map(this::roomCard).toList();
        String summary = rooms.stream()
                .map(room -> RoomNames.of(room.roomId()) + " " + room.status()
                        .map(s -> s.inSession() ? s.currentNumber() + " 號" : "未看診")
                        .orElse("無資料"))
                .collect(Collectors.joining("、"));
        return message(altTextPrefix + "：" + summary, new FlexCarousel(bubbles));
    }

    private FlexBubble roomCard(RoomOption room) {
        String roomName = RoomNames.of(room.roomId());
        Optional<RoomStatus> status = room.status();
        boolean inSession = status.map(RoomStatus::inSession).orElse(false);
        String department = status.map(RoomStatus::department).orElse(null);

        List<FlexComponent> body = new ArrayList<>();
        if (inSession) {
            RoomStatus s = status.get();
            body.add(bigCenter("目前叫號", String.valueOf(s.currentNumber()), Tone.INFO.color));
            if (s.doctorName() != null) {
                body.add(row("醫師", s.doctorName()));
            }
        } else {
            body.add(bigCenter(null, status.isPresent() ? "未看診" : "無資料", Tone.NEUTRAL.color));
        }
        status.flatMap(MessageFactory::updateTimeNote).ifPresent(body::add);

        List<FlexComponent> footer = inSession
                ? List.of(postbackButton("追蹤" + roomName, PostbackActions.selectRoom(room.roomId()),
                        "追蹤" + roomName, true, Tone.INFO))
                : List.of();
        Tone tone = inSession ? Tone.INFO : Tone.NEUTRAL;
        return bubble(tone, roomName, department == null ? clinicName() : department, body, footer,
                FlexBubble.Size.KILO);
    }

    public record RoomOption(int roomId, Optional<RoomStatus> status) {
    }

    // ── 通知門檻 ──────────────────────────────────────────

    /**
     * 通知設定卡片：目前門檻、使用說明，以及「自訂門檻」「重設為預設」按鈕。
     * 「自訂門檻」會開啟鍵盤並預填「設定門檻 」，使用者補上數字即可送出。
     */
    public Message thresholdSettings(String title, ThresholdSettings settings) {
        List<FlexComponent> body = new ArrayList<>();
        body.add(row("目前門檻", settings.custom() ? "自訂" : "系統預設"));
        body.addAll(chips(settings.thresholds().stream()
                .map(threshold -> threshold == 0 ? "到號" : "剩 " + threshold + " 位")
                .toList(), Tone.INFO));
        body.add(note(describeThresholds(settings.thresholds()) + "通知您"));
        body.add(separator());
        body.add(commandRow("自訂門檻", "點「自訂門檻」後輸入剩幾位時通知，以空白分隔，例如：設定門檻 15 8 3"));
        body.add(commandRow("規則", "最多 5 個，範圍 1～50；到號一定會通知"));
        body.add(commandRow("重設為預設", "恢復為 " + formatThresholds(settings.defaults())));
        String altText = title + "：" + formatThresholds(settings.thresholds());
        return message(altText, bubble(Tone.INFO, title, clinicName(), body, List.of(
                fillInButton("自訂門檻", PostbackActions.customThresholds(), "設定門檻 ", true, Tone.INFO),
                messageButton("重設為預設", "重設門檻", false, Tone.INFO))));
    }

    // ── 說明 / 歡迎 / 通用提示 ────────────────────────────

    public Message help() {
        return message("使用說明：追蹤、目前狀態、取消追蹤、診間、設定門檻",
                bubble(Tone.INFO, "使用說明", clinicName(), helpRows(), List.of(
                        messageButton("開始追蹤", "追蹤", true, Tone.INFO),
                        messageButton("查看診間", "診間", false, Tone.INFO))));
    }

    public Message welcome(String displayName) {
        String greeting = displayName == null ? "您好！" : displayName + "，您好！";
        List<FlexComponent> body = List.of(
                paragraph(greeting + "快輪到您看診時，我會主動通知您。"),
                separator(),
                commandRow("① 點選單的「追蹤看診號碼」", "選擇診間"),
                commandRow("② 輸入您的看診號碼", "例如：56"),
                commandRow("③ 等待通知", describeThresholds(
                        ThresholdResolver.normalize(notificationProperties.defaultThresholds())) + "提醒您"),
                note("點選單右下角 ⚙ 可調整通知門檻；輸入「幫助」可查看所有指令"));
        return message("歡迎使用" + clinicName() + "看診進度通知",
                bubble(Tone.INFO, "歡迎使用看診進度通知", clinicName(), body, List.of(
                        messageButton("開始追蹤", "追蹤", true, Tone.INFO),
                        messageButton("查看診間", "診間", false, Tone.INFO))));
    }

    /**
     * 通用提示卡片（取消成功、錯誤、格式說明等）。
     *
     * @param button 選用的操作按鈕，可為 null
     */
    public Message notice(Tone tone, String title, String text, FlexComponent button) {
        return message(title + "：" + text, bubble(tone, title, clinicName(), List.of(paragraph(text)),
                button == null ? List.of() : List.of(button)));
    }

    public Message notice(Tone tone, String title, String text) {
        return notice(tone, title, text, null);
    }

    public Message cancelled() {
        return notice(Tone.NEUTRAL, "已取消追蹤", "需要時可隨時重新追蹤。",
                messageButton("開始追蹤", "追蹤", true, Tone.INFO));
    }

    public Message unknownCommand() {
        return notice(Tone.NEUTRAL, "看不懂這個指令", "可使用下方選單操作，或輸入「幫助」查看所有指令。",
                messageButton("使用說明", "幫助", true, Tone.INFO));
    }

    // ── 格式化 ────────────────────────────────────────────

    private List<FlexComponent> helpRows() {
        return List.of(
                commandRow("追蹤", "選擇診間後輸入號碼，即可開始追蹤"),
                commandRow("追蹤 2診 56號", "直接開始追蹤（新的追蹤會取代舊的）"),
                commandRow("目前狀態", "查看追蹤進度與預估時間"),
                commandRow("取消追蹤", "停止追蹤"),
                commandRow("診間", "查看所有診間目前號碼"),
                commandRow("通知設定（選單右下角 ⚙）", "查看、自訂或重設通知門檻"),
                commandRow("設定門檻 10 5", "直接設定剩幾位時通知（到號一定會通知）"));
    }

    private String clinicName() {
        return clinicProperties.name();
    }

    private static String info(RoomStatus status) {
        return Arrays.stream(new String[] {status.department(), status.inSession() ? status.doctorName() : null})
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining(" "));
    }

    private static Optional<FlexComponent> updateTimeNote(RoomStatus status) {
        return Optional.ofNullable(status.updateTime())
                .map(time -> note("資料更新：" + time.format(UPDATE_TIME)));
    }

    /** 例：「剩 10、3 位及到號時」 */
    static String describeThresholds(List<Integer> thresholds) {
        String positives = thresholds.stream()
                .filter(threshold -> threshold > 0)
                .map(String::valueOf)
                .collect(Collectors.joining("、"));
        return positives.isEmpty() ? "到號時" : "剩 " + positives + " 位及到號時";
    }

    public static String formatThresholds(List<Integer> thresholds) {
        return thresholds.stream()
                .map(threshold -> threshold == 0 ? "到號" : String.valueOf(threshold))
                .collect(Collectors.joining("、"));
    }

    public static String formatEta(Optional<Long> etaMinutes) {
        return etaMinutes.map(minutes -> minutes < 1 ? "1 分鐘內" : "約 " + minutes + " 分鐘").orElse("資料不足");
    }

}
