package com.everythingcanbe.linebotclinicnotifysystem.line;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.linecorp.bot.messaging.model.FlexBox;
import com.linecorp.bot.messaging.model.FlexBubble;
import com.linecorp.bot.messaging.model.FlexComponent;
import com.linecorp.bot.messaging.model.FlexMessage;
import com.linecorp.bot.messaging.model.FlexText;
import com.linecorp.bot.messaging.model.Message;
import com.linecorp.bot.messaging.model.TextMessage;

import com.everythingcanbe.linebotclinicnotifysystem.config.ClinicProperties;
import com.everythingcanbe.linebotclinicnotifysystem.provider.RoomStatus;

/**
 * 建立 LINE 訊息（Flex 卡片與純文字）。
 */
@Component
public class MessageFactory {

    private static final String COLOR_PROGRESS = "#F39C12";
    private static final String COLOR_ARRIVED = "#27AE60";
    private static final String COLOR_ALERT = "#C0392B";
    private static final String COLOR_INFO = "#2E86C1";
    private static final String COLOR_LABEL = "#888888";

    private final ClinicProperties clinicProperties;

    public MessageFactory(ClinicProperties clinicProperties) {
        this.clinicProperties = clinicProperties;
    }

    public Message text(String text) {
        return new TextMessage(text);
    }

    public Message progress(RoomStatus status, int targetNumber, Optional<Long> etaMinutes) {
        int remaining = targetNumber - status.currentNumber();
        List<FlexComponent> rows = new ArrayList<>();
        rows.add(heading(status.roomName()));
        if (status.doctorName() != null) {
            rows.add(row("醫師", status.doctorName()));
        }
        rows.add(row("目前", status.currentNumber() + "號"));
        rows.add(row("您是", targetNumber + "號"));
        rows.add(row("剩餘", remaining + "位"));
        rows.add(row("預估", formatEta(etaMinutes)));
        String altText = "即將輪到您看診：%s 目前 %d 號，您是 %d 號，剩餘 %d 位"
                .formatted(status.roomName(), status.currentNumber(), targetNumber, remaining);
        return card(altText, "即將輪到您看診", COLOR_PROGRESS, rows);
    }

    public Message arrived(RoomStatus status, int targetNumber) {
        List<FlexComponent> rows = List.of(
                heading(status.roomName()),
                line("已輪到 " + targetNumber + " 號"));
        String altText = "請立即報到：%s 已輪到 %d 號".formatted(status.roomName(), targetNumber);
        return card(altText, "請立即報到", COLOR_ARRIVED, rows);
    }

    public Message missed(RoomStatus status, int targetNumber) {
        List<FlexComponent> rows = List.of(
                heading(status.roomName()),
                row("目前", status.currentNumber() + "號"),
                row("您是", targetNumber + "號"),
                line("您的號碼已過號，請洽櫃台。追蹤已結束。"));
        String altText = "%s 目前 %d 號，您的 %d 號已過號，請洽櫃台"
                .formatted(status.roomName(), status.currentNumber(), targetNumber);
        return card(altText, "已過號", COLOR_ALERT, rows);
    }

    public Message sessionReset(RoomStatus status, int targetNumber) {
        List<FlexComponent> rows = List.of(
                heading(status.roomName()),
                row("目前", status.currentNumber() + "號"),
                row("您是", targetNumber + "號"),
                line("看診號碼已重新開始，原追蹤已結束。如需繼續請重新輸入追蹤。"));
        String altText = "%s 看診號碼已重新開始，%d 號的追蹤已結束".formatted(status.roomName(), targetNumber);
        return card(altText, "追蹤已結束", COLOR_ALERT, rows);
    }

    public Message trackingStatus(RoomStatus status, int targetNumber, Optional<Long> etaMinutes) {
        int remaining = targetNumber - status.currentNumber();
        List<FlexComponent> rows = List.of(
                heading(status.roomName()),
                row("目前", status.currentNumber() + "號"),
                row("您是", targetNumber + "號"),
                row("剩餘", Math.max(remaining, 0) + "位"),
                row("預估", formatEta(etaMinutes)));
        String altText = "%s 目前 %d 號，您是 %d 號，剩餘 %d 位"
                .formatted(status.roomName(), status.currentNumber(), targetNumber, Math.max(remaining, 0));
        return card(altText, clinicProperties.name(), COLOR_INFO, rows);
    }

    public static String formatThresholds(List<Integer> thresholds) {
        return thresholds.stream()
                .map(threshold -> threshold == 0 ? "到號" : String.valueOf(threshold))
                .collect(Collectors.joining("、"));
    }

    public static String formatEta(Optional<Long> etaMinutes) {
        return etaMinutes.map(minutes -> minutes < 1 ? "1分鐘內" : "約" + minutes + "分鐘").orElse("資料不足");
    }

    private FlexMessage card(String altText, String title, String color, List<FlexComponent> rows) {
        FlexBox header = new FlexBox.Builder(FlexBox.Layout.VERTICAL, List.of(
                new FlexText.Builder().text(title).size("lg").weight(FlexText.Weight.BOLD).color("#FFFFFF").build()))
                .backgroundColor(color)
                .paddingAll("16px")
                .build();
        FlexBox body = new FlexBox.Builder(FlexBox.Layout.VERTICAL, rows)
                .spacing("sm")
                .paddingAll("16px")
                .build();
        FlexBubble bubble = new FlexBubble.Builder().header(header).body(body).build();
        return new FlexMessage(altText, bubble);
    }

    private static FlexComponent heading(String text) {
        return new FlexText.Builder().text(text).size("xl").weight(FlexText.Weight.BOLD).build();
    }

    private static FlexComponent line(String text) {
        return new FlexText.Builder().text(text).size("md").wrap(true).build();
    }

    private static FlexComponent row(String label, String value) {
        return new FlexBox.Builder(FlexBox.Layout.BASELINE, List.of(
                new FlexText.Builder().text(label).size("sm").color(COLOR_LABEL).flex(2).build(),
                new FlexText.Builder().text(value).size("md").weight(FlexText.Weight.BOLD).flex(5).wrap(true)
                        .build()))
                .build();
    }

}
