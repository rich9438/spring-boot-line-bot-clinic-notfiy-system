package com.everythingcanbe.linebotclinicnotifysystem.admin;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AdminCommandParserTest {

    private final AdminCommandParser parser = new AdminCommandParser();

    @Test
    void simpleCommands() {
        assertThat(parser.parse("狀態")).isInstanceOf(AdminCommand.Status.class);
        assertThat(parser.parse("STATUS")).isInstanceOf(AdminCommand.Status.class);
        assertThat(parser.parse("額度")).isInstanceOf(AdminCommand.Quota.class);
        assertThat(parser.parse("Webhook")).isInstanceOf(AdminCommand.WebhookTest.class);
        assertThat(parser.parse("摘要")).isInstanceOf(AdminCommand.Summary.class);
        assertThat(parser.parse("範例")).isInstanceOf(AdminCommand.Samples.class);
        assertThat(parser.parse("抓取")).isInstanceOf(AdminCommand.Poll.class);
        assertThat(parser.parse("選單")).isInstanceOf(AdminCommand.RichMenus.class);
        assertThat(parser.parse("選單清理")).isInstanceOf(AdminCommand.RichMenuCleanup.class);
        assertThat(parser.parse("幫助")).isInstanceOf(AdminCommand.Help.class);
        assertThat(parser.parse("你好")).isInstanceOf(AdminCommand.Unknown.class);
    }

    @Test
    void roomAndSample() {
        assertThat(parser.parse("診間 2")).isEqualTo(new AdminCommand.Room(2));
        assertThat(parser.parse("診間２")).isEqualTo(new AdminCommand.Room(2));
        assertThat(parser.parse("診間")).isInstanceOf(AdminCommand.Invalid.class);
        assertThat(parser.parse("範例 04")).isEqualTo(new AdminCommand.SendSample("04"));
    }

    @Test
    void errorsAndLog() {
        assertThat(parser.parse("錯誤")).isEqualTo(new AdminCommand.Errors(10, null));
        assertThat(parser.parse("錯誤 20")).isEqualTo(new AdminCommand.Errors(20, null));
        assertThat(parser.parse("錯誤 999")).isEqualTo(new AdminCommand.Errors(30, null));
        assertThat(parser.parse("log")).isEqualTo(new AdminCommand.Errors(10, null));
        assertThat(parser.parse("LOG RestClient")).isEqualTo(new AdminCommand.Errors(30, "RestClient"));
    }

    @Test
    void lookup() {
        assertThat(parser.parse("查詢 小明")).isEqualTo(new AdminCommand.Lookup("小明"));
        assertThat(parser.parse("查詢")).isInstanceOf(AdminCommand.Invalid.class);
    }

    @Test
    void jsonIsKeptVerbatim() {
        String json = "  {\"type\":\"bubble\",\"body\":{\"text\":\"全形，標點\"}}  ";

        assertThat(parser.parse(json)).isEqualTo(new AdminCommand.FlexPreview(json.strip()));
    }

    @Test
    void cleanupConfirmPostback() {
        assertThat(parser.parsePostback(AdminCommandParser.CLEANUP_CONFIRM_DATA))
                .isInstanceOf(AdminCommand.RichMenuCleanupConfirm.class);
        assertThat(parser.parsePostback("action=other")).isInstanceOf(AdminCommand.Unknown.class);
    }

}
