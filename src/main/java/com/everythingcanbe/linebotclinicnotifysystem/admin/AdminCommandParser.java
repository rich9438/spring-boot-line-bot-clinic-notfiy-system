package com.everythingcanbe.linebotclinicnotifysystem.admin;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.everythingcanbe.linebotclinicnotifysystem.line.CommandParser;

/**
 * 解析後台管理指令。
 */
@Component
@ConditionalOnAdminEnabled
public class AdminCommandParser {

    static final int DEFAULT_LOG_LIMIT = 10;
    static final int MAX_LOG_LIMIT = 30;
    static final String CLEANUP_CONFIRM_DATA = "action=admin-richmenu-cleanup";

    private static final Pattern ROOM = Pattern.compile("^診間\\s*(\\d{1,2})$");
    private static final Pattern ERRORS = Pattern.compile("^(?:錯誤|errors?)(?:\\s+(\\d{1,3}))?$");
    private static final Pattern LOG = Pattern.compile("^log(?:\\s+(.+))?$");
    private static final Pattern LOOKUP = Pattern.compile("^查詢(?:\\s+(.+))?$");
    private static final Pattern SAMPLE = Pattern.compile("^範例\\s+(\\S+)$");

    public AdminCommand parse(String rawText) {
        // JSON 不做全形轉換，以免改動卡片內的全形標點
        String raw = rawText == null ? "" : rawText.strip();
        if (raw.startsWith("{")) {
            return new AdminCommand.FlexPreview(raw);
        }
        String text = CommandParser.normalize(raw);
        String lower = text.toLowerCase(Locale.ROOT);

        switch (lower) {
            case "狀態", "status" -> {
                return new AdminCommand.Status();
            }
            case "額度", "quota" -> {
                return new AdminCommand.Quota();
            }
            case "webhook" -> {
                return new AdminCommand.WebhookTest();
            }
            case "摘要", "summary" -> {
                return new AdminCommand.Summary();
            }
            case "範例", "samples" -> {
                return new AdminCommand.Samples();
            }
            case "抓取", "poll" -> {
                return new AdminCommand.Poll();
            }
            case "選單" -> {
                return new AdminCommand.RichMenus();
            }
            case "選單清理" -> {
                return new AdminCommand.RichMenuCleanup();
            }
            case "幫助", "help", "?" -> {
                return new AdminCommand.Help();
            }
            case "診間" -> {
                return new AdminCommand.Invalid("格式：診間 2");
            }
            default -> {
                // 其餘指令於下方以正規式比對
            }
        }

        Matcher room = ROOM.matcher(text);
        if (room.matches()) {
            return new AdminCommand.Room(Integer.parseInt(room.group(1)));
        }
        Matcher errors = ERRORS.matcher(lower);
        if (errors.matches()) {
            int limit = errors.group(1) == null ? DEFAULT_LOG_LIMIT : Integer.parseInt(errors.group(1));
            return new AdminCommand.Errors(Math.clamp(limit, 1, MAX_LOG_LIMIT), null);
        }
        Matcher log = LOG.matcher(lower);
        if (log.matches()) {
            // 關鍵字取原始大小寫
            String keyword = log.group(1) == null ? null : text.substring(log.start(1)).trim();
            return new AdminCommand.Errors(keyword == null ? DEFAULT_LOG_LIMIT : MAX_LOG_LIMIT, keyword);
        }
        Matcher lookup = LOOKUP.matcher(text);
        if (lookup.matches()) {
            return lookup.group(1) == null
                    ? new AdminCommand.Invalid("格式：查詢 顯示名稱 或 查詢 Uxxxxxxxx（userId）")
                    : new AdminCommand.Lookup(lookup.group(1).trim());
        }
        Matcher sample = SAMPLE.matcher(text);
        if (sample.matches()) {
            return new AdminCommand.SendSample(sample.group(1));
        }
        return new AdminCommand.Unknown(text);
    }

    public AdminCommand parsePostback(String data) {
        return CLEANUP_CONFIRM_DATA.equals(data) ? new AdminCommand.RichMenuCleanupConfirm()
                : new AdminCommand.Unknown(data);
    }

}
