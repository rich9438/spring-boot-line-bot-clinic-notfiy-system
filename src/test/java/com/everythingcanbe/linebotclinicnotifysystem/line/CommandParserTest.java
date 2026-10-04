package com.everythingcanbe.linebotclinicnotifysystem.line;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CommandParserTest {

    private final CommandParser parser = new CommandParser();

    @ParameterizedTest
    @ValueSource(strings = {"追蹤 2診 56號", "追蹤 二診 56", "追蹤 2 56", "追蹤2診56號", "  追蹤　２診　５６號 ", "追蹤 2診 56"})
    void parsesTrackVariants(String text) {
        assertThat(parser.parse(text)).isEqualTo(new Command.Track(2, 56));
    }

    @Test
    void parsesTwoDigitChineseRoom() {
        assertThat(parser.parse("追蹤 十一診 8號")).isEqualTo(new Command.Track(11, 8));
    }

    @ParameterizedTest
    @ValueSource(strings = {"追蹤 abc", "追蹤 256", "追蹤 2診 12345號"})
    void invalidTrackShowsUsage(String text) {
        assertThat(parser.parse(text)).isEqualTo(new Command.Invalid(CommandParser.TRACK_USAGE));
    }

    @Test
    void bareTrackOpensRoomPicker() {
        assertThat(parser.parse("追蹤")).isInstanceOf(Command.ChooseRoom.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"追蹤 2診", "追蹤 二診", "追蹤2", "追蹤 ２診"})
    void trackWithRoomOnlySelectsRoom(String text) {
        assertThat(parser.parse(text)).isEqualTo(new Command.SelectRoom(2));
    }

    @ParameterizedTest
    @ValueSource(strings = {"56", "56號", " ５６ "})
    void numberOnly(String text) {
        assertThat(parser.parse(text)).isEqualTo(new Command.Number(56));
    }

    @Test
    void parsesPostback() {
        assertThat(parser.parsePostback(PostbackActions.selectRoom(3))).isEqualTo(new Command.SelectRoom(3));
        assertThat(parser.parsePostback(PostbackActions.customThresholds())).isInstanceOf(Command.NoReply.class);
        assertThat(parser.parsePostback("action=unknown")).isInstanceOf(Command.Unknown.class);
        assertThat(parser.parsePostback("action=select-room&room=x")).isInstanceOf(Command.Unknown.class);
        assertThat(parser.parsePostback(null)).isInstanceOf(Command.Unknown.class);
    }

    @Test
    void parsesSimpleCommands() {
        assertThat(parser.parse("目前狀態")).isInstanceOf(Command.Status.class);
        assertThat(parser.parse("取消追蹤")).isInstanceOf(Command.Cancel.class);
        assertThat(parser.parse("取消")).isInstanceOf(Command.Cancel.class);
        assertThat(parser.parse("診間")).isInstanceOf(Command.Rooms.class);
        assertThat(parser.parse("查看門檻")).isInstanceOf(Command.ShowThresholds.class);
        assertThat(parser.parse("通知設定")).isInstanceOf(Command.ShowThresholds.class);
        assertThat(parser.parse("重設門檻")).isInstanceOf(Command.ResetThresholds.class);
        assertThat(parser.parse("幫助")).isInstanceOf(Command.Help.class);
        assertThat(parser.parse("HELP")).isInstanceOf(Command.Help.class);
        assertThat(parser.parse("？")).isInstanceOf(Command.Help.class);
    }

    @Test
    void parsesThresholds() {
        assertThat(parser.parse("設定門檻 10 5")).isEqualTo(new Command.SetThresholds(List.of(10, 5)));
        assertThat(parser.parse("設定門檻15,8，3")).isEqualTo(new Command.SetThresholds(List.of(15, 8, 3)));
        assertThat(parser.parse("設定門檻 10、5")).isEqualTo(new Command.SetThresholds(List.of(10, 5)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"設定門檻", "設定門檻 a b", "設定門檻 -1"})
    void invalidThresholdsShowUsage(String text) {
        assertThat(parser.parse(text)).isEqualTo(new Command.Invalid(CommandParser.THRESHOLD_USAGE));
    }

    @Test
    void unknownText() {
        assertThat(parser.parse("你好")).isEqualTo(new Command.Unknown("你好"));
    }

}
