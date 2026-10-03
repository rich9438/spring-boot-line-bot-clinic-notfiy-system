package com.everythingcanbe.linebotclinicnotifysystem.line;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.everythingcanbe.linebotclinicnotifysystem.provider.RoomNames;

/**
 * 將文字訊息解析為 {@link Command}。接受全形數字與全形空白。
 */
@Component
public class CommandParser {

    static final String TRACK_USAGE = "格式：追蹤 2診 56號";
    static final String THRESHOLD_USAGE = "格式：設定門檻 10 5（以空白或逗號分隔）";

    private static final Pattern TRACK = Pattern.compile(
            "^追蹤\\s*([0-9一二三四五六七八九十]+)(?:\\s*診\\s*|\\s+)(\\d+)\\s*號?$");
    private static final Pattern SET_THRESHOLDS = Pattern.compile("^設定門檻\\s*(.*)$");
    private static final Pattern THRESHOLD_SEPARATOR = Pattern.compile("[\\s,，、]+");

    private static final Set<String> STATUS = Set.of("目前狀態", "狀態", "查詢");
    private static final Set<String> CANCEL = Set.of("取消追蹤", "取消");
    private static final Set<String> ROOMS = Set.of("診間", "所有診間");
    private static final Set<String> SHOW_THRESHOLDS = Set.of("查看門檻", "門檻");
    private static final Set<String> RESET_THRESHOLDS = Set.of("重設門檻");
    private static final Set<String> HELP = Set.of("幫助", "help", "?", "說明");

    public Command parse(String rawText) {
        String text = normalize(rawText);

        if (STATUS.contains(text)) {
            return new Command.Status();
        }
        if (CANCEL.contains(text)) {
            return new Command.Cancel();
        }
        if (ROOMS.contains(text)) {
            return new Command.Rooms();
        }
        if (SHOW_THRESHOLDS.contains(text)) {
            return new Command.ShowThresholds();
        }
        if (RESET_THRESHOLDS.contains(text)) {
            return new Command.ResetThresholds();
        }
        if (HELP.contains(text.toLowerCase())) {
            return new Command.Help();
        }
        if (text.startsWith("追蹤")) {
            return parseTrack(text);
        }
        Matcher thresholds = SET_THRESHOLDS.matcher(text);
        if (thresholds.matches()) {
            return parseThresholds(thresholds.group(1));
        }
        return new Command.Unknown(text);
    }

    private Command parseTrack(String text) {
        Matcher matcher = TRACK.matcher(text);
        if (!matcher.matches()) {
            return new Command.Invalid(TRACK_USAGE);
        }
        Integer roomId = RoomNames.parse(matcher.group(1));
        if (roomId == null || matcher.group(2).length() > 4) {
            return new Command.Invalid(TRACK_USAGE);
        }
        return new Command.Track(roomId, Integer.parseInt(matcher.group(2)));
    }

    private Command parseThresholds(String values) {
        if (values.isBlank()) {
            return new Command.Invalid(THRESHOLD_USAGE);
        }
        String[] tokens = THRESHOLD_SEPARATOR.split(values.trim());
        if (Arrays.stream(tokens).anyMatch(token -> !token.matches("\\d{1,3}"))) {
            return new Command.Invalid(THRESHOLD_USAGE);
        }
        List<Integer> thresholds = Arrays.stream(tokens).map(Integer::valueOf).toList();
        return new Command.SetThresholds(thresholds);
    }

    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return Normalizer.normalize(text, Normalizer.Form.NFKC)
                .trim()
                .replaceAll("\\s+", " ");
    }

}
