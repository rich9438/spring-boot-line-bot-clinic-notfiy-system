package com.everythingcanbe.linebotclinicnotifysystem.line;

import java.util.ArrayList;
import java.util.List;

import com.linecorp.bot.messaging.model.Action;
import com.linecorp.bot.messaging.model.FlexBox;
import com.linecorp.bot.messaging.model.FlexBubble;
import com.linecorp.bot.messaging.model.FlexButton;
import com.linecorp.bot.messaging.model.FlexComponent;
import com.linecorp.bot.messaging.model.FlexContainer;
import com.linecorp.bot.messaging.model.FlexMessage;
import com.linecorp.bot.messaging.model.FlexSeparator;
import com.linecorp.bot.messaging.model.FlexText;
import com.linecorp.bot.messaging.model.MessageAction;
import com.linecorp.bot.messaging.model.PostbackAction;

/**
 * Flex Message 共用元件，統一所有卡片的版型與配色。
 *
 * <pre>
 * ┌──────────────────────┐
 * │ header：標題（色帶） │  ← 顏色代表狀態（{@link Tone}）
 * │         副標題       │
 * ├──────────────────────┤
 * │ body：診別、號碼、說明│
 * ├──────────────────────┤
 * │ footer：操作按鈕     │  ← 選用
 * └──────────────────────┘
 * </pre>
 */
final class FlexParts {

    static final String TEXT = "#2C3E50";
    static final String LABEL = "#8A9499";
    static final String PANEL = "#F2F5F7";

    private FlexParts() {
    }

    /**
     * 卡片狀態色：資訊（藍）、即將輪到（橘）、到號（綠）、警示（紅）、無作用（灰）。
     */
    enum Tone {
        INFO("#2E86C1"),
        PROGRESS("#E67E22"),
        ARRIVED("#27AE60"),
        ALERT("#C0392B"),
        NEUTRAL("#7F8C8D");

        final String color;

        Tone(String color) {
            this.color = color;
        }
    }

    static FlexMessage message(String altText, FlexContainer container) {
        return new FlexMessage(altText, container);
    }

    static FlexBubble bubble(Tone tone, String title, String subtitle, List<FlexComponent> body,
            List<FlexComponent> footerButtons) {
        return bubble(tone, title, subtitle, body, footerButtons, null);
    }

    static FlexBubble bubble(Tone tone, String title, String subtitle, List<FlexComponent> body,
            List<FlexComponent> footerButtons, FlexBubble.Size size) {
        FlexBubble.Builder builder = new FlexBubble.Builder()
                .header(header(tone, title, subtitle))
                .body(new FlexBox.Builder(FlexBox.Layout.VERTICAL, body)
                        .spacing("md")
                        .paddingAll("16px")
                        .build());
        if (!footerButtons.isEmpty()) {
            builder.footer(new FlexBox.Builder(FlexBox.Layout.HORIZONTAL, footerButtons)
                    .spacing("sm")
                    .paddingAll("12px")
                    .paddingTop("0px")
                    .build());
        }
        if (size != null) {
            builder.size(size);
        }
        return builder.build();
    }

    private static FlexBox header(Tone tone, String title, String subtitle) {
        List<FlexComponent> contents = new ArrayList<>();
        contents.add(new FlexText.Builder().text(title).size("xl").weight(FlexText.Weight.BOLD)
                .color("#FFFFFF").wrap(true).build());
        if (subtitle != null) {
            contents.add(new FlexText.Builder().text(subtitle).size("xs").color("#FFFFFFCC").build());
        }
        return new FlexBox.Builder(FlexBox.Layout.VERTICAL, contents)
                .backgroundColor(tone.color)
                .paddingAll("16px")
                .spacing("xs")
                .build();
    }

    /** 診別標題，例：「二診」＋「婦產科 吳瑞聰」 */
    static FlexComponent roomTitle(String roomName, String info) {
        List<FlexComponent> contents = new ArrayList<>();
        contents.add(new FlexText.Builder().text(roomName).size("xxl").weight(FlexText.Weight.BOLD).color(TEXT)
                .flex(0).build());
        if (info != null && !info.isBlank()) {
            contents.add(new FlexText.Builder().text(info).size("sm").color(LABEL).align(FlexText.Align.END)
                    .gravity(FlexText.Gravity.BOTTOM).wrap(true).build());
        }
        return new FlexBox.Builder(FlexBox.Layout.BASELINE, contents).spacing("md").build();
    }

    /** 「目前叫號 / 您的號碼」並排的大號碼面板 */
    static FlexComponent numberPanel(String currentValue, Tone currentTone, int targetNumber) {
        return new FlexBox.Builder(FlexBox.Layout.HORIZONTAL, List.of(
                numberCell("目前叫號", currentValue, currentTone.color),
                new FlexSeparator(null, "#DDE3E7"),
                numberCell("您的號碼", String.valueOf(targetNumber), TEXT)))
                .backgroundColor(PANEL)
                .cornerRadius("12px")
                .paddingAll("12px")
                .build();
    }

    private static FlexComponent numberCell(String label, String value, String color) {
        return new FlexBox.Builder(FlexBox.Layout.VERTICAL, List.of(
                new FlexText.Builder().text(label).size("xs").color(LABEL).align(FlexText.Align.CENTER).build(),
                new FlexText.Builder().text(value).size("3xl").weight(FlexText.Weight.BOLD).color(color)
                        .align(FlexText.Align.CENTER).build()))
                .flex(1)
                .build();
    }

    /** 置中的大字，例：到號卡片的「56 號」、診間卡片的「未看診」 */
    static FlexComponent bigCenter(String label, String value, String color) {
        List<FlexComponent> contents = new ArrayList<>();
        if (label != null) {
            contents.add(new FlexText.Builder().text(label).size("xs").color(LABEL).align(FlexText.Align.CENTER)
                    .build());
        }
        contents.add(new FlexText.Builder().text(value).size("4xl").weight(FlexText.Weight.BOLD).color(color)
                .align(FlexText.Align.CENTER).build());
        return new FlexBox.Builder(FlexBox.Layout.VERTICAL, contents)
                .backgroundColor(PANEL)
                .cornerRadius("12px")
                .paddingAll("12px")
                .build();
    }

    static FlexComponent row(String label, String value) {
        return row(label, value, TEXT);
    }

    static FlexComponent row(String label, String value, String valueColor) {
        return new FlexBox.Builder(FlexBox.Layout.BASELINE, List.of(
                new FlexText.Builder().text(label).size("sm").color(LABEL).flex(2).build(),
                new FlexText.Builder().text(value).size("md").weight(FlexText.Weight.BOLD).color(valueColor)
                        .flex(5).wrap(true).build()))
                .build();
    }

    static FlexComponent paragraph(String text) {
        return new FlexText.Builder().text(text).size("md").color(TEXT).wrap(true).build();
    }

    static FlexComponent note(String text) {
        return new FlexText.Builder().text(text).size("xs").color(LABEL).wrap(true).build();
    }

    static FlexComponent separator() {
        return new FlexSeparator(null, "#E5E9EC");
    }

    /** 標籤（chip）列，每列最多 3 個；固定寬度讓各列對齊 */
    static List<FlexComponent> chips(List<String> labels, Tone tone) {
        List<FlexComponent> rows = new ArrayList<>();
        for (int i = 0; i < labels.size(); i += 3) {
            List<FlexComponent> chips = new ArrayList<>();
            for (String label : labels.subList(i, Math.min(i + 3, labels.size()))) {
                chips.add(new FlexBox.Builder(FlexBox.Layout.VERTICAL, List.of(
                        new FlexText.Builder().text(label).size("sm").weight(FlexText.Weight.BOLD)
                                .color(tone.color).align(FlexText.Align.CENTER).build()))
                        .backgroundColor(PANEL)
                        .cornerRadius("20px")
                        .paddingAll("8px")
                        .width("31%")
                        .flex(0)
                        .build());
            }
            rows.add(new FlexBox.Builder(FlexBox.Layout.HORIZONTAL, chips).spacing("sm").build());
        }
        return rows;
    }

    /** 指令說明列：左側粗體指令、右側說明 */
    static FlexComponent commandRow(String command, String description) {
        return new FlexBox.Builder(FlexBox.Layout.VERTICAL, List.of(
                new FlexText.Builder().text(command).size("sm").weight(FlexText.Weight.BOLD).color(TEXT).build(),
                new FlexText.Builder().text(description).size("xs").color(LABEL).wrap(true).build()))
                .build();
    }

    static FlexComponent messageButton(String label, String text, boolean primary, Tone tone) {
        return button(new MessageAction(label, text), primary, tone);
    }

    /** Postback 按鈕；openKeyboard 為 true 時點選後自動開啟鍵盤 */
    static FlexComponent postbackButton(String label, String data, String displayText, boolean openKeyboard,
            Tone tone) {
        PostbackAction action = new PostbackAction(label, data, displayText, null,
                openKeyboard ? PostbackAction.InputOption.OPEN_KEYBOARD : null, null);
        return button(action, true, tone);
    }

    private static FlexComponent button(Action action, boolean primary, Tone tone) {
        FlexButton.Builder builder = new FlexButton.Builder(action)
                .height(FlexButton.Height.SM)
                .flex(1);
        if (primary) {
            builder.style(FlexButton.Style.PRIMARY).color(tone.color);
        } else {
            builder.style(FlexButton.Style.SECONDARY);
        }
        return builder.build();
    }

}
