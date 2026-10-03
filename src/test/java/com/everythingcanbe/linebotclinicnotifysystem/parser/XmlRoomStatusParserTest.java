package com.everythingcanbe.linebotclinicnotifysystem.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import com.everythingcanbe.linebotclinicnotifysystem.provider.RoomStatus;

class XmlRoomStatusParserTest {

    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");

    private final XmlRoomStatusParser parser = new XmlRoomStatusParser(
            Clock.fixed(LocalDateTime.of(2026, 10, 3, 15, 0).atZone(TAIPEI).toInstant(), TAIPEI));

    @Test
    void parsesInSessionRoomWithBomAndLeadingZero() throws IOException {
        RoomStatus status = parser.parse(read("room2-in-session.xml"), "wuobs");

        assertThat(status.providerCode()).isEqualTo("wuobs");
        assertThat(status.roomId()).isEqualTo(2);
        assertThat(status.roomName()).isEqualTo("二診");
        assertThat(status.doctorName()).isEqualTo("吳瑞聰");
        assertThat(status.department()).isEqualTo("婦產科");
        assertThat(status.currentNumber()).isEqualTo(52);
        assertThat(status.inSession()).isTrue();
        assertThat(status.updateTime()).isEqualTo(LocalDateTime.of(2026, 10, 3, 12, 17, 7, 342_000_000));
    }

    @Test
    void emptyNumberIsNotInSession() throws IOException {
        RoomStatus status = parser.parse(read("room2-empty-number.xml"), "wuobs");

        assertThat(status.inSession()).isFalse();
        assertThat(status.currentNumber()).isNull();
    }

    @Test
    void zeroNumberIsNotInSession() throws IOException {
        RoomStatus status = parser.parse(read("room3-zero.xml"), "wuobs");

        assertThat(status.roomId()).isEqualTo(3);
        assertThat(status.department()).isEqualTo("小兒科");
        assertThat(status.doctorName()).isNull();
        assertThat(status.inSession()).isFalse();
    }

    @Test
    void staleExecTimeIsNotInSession() {
        byte[] xml = """
                <RB><Datas><RoomInfo Number1="35" EXECTIME1="20261001200549826" DOC1="吳瑞聰" Title1="婦產科" Title2="1" /></Datas>
                <RET RETCODE="0000" DESC="" /></RB>""".getBytes();

        RoomStatus status = parser.parse(xml, "wuobs");

        assertThat(status.inSession()).isFalse();
        assertThat(status.currentNumber()).isNull();
    }

    @Test
    void realStaleSampleIsNotInSession() throws IOException {
        RoomStatus status = parser.parse(read("room1-stale.xml"), "wuobs");

        assertThat(status.roomName()).isEqualTo("一診");
        assertThat(status.inSession()).isFalse();
    }

    @Test
    void errorRetCodeThrows() {
        assertThatThrownBy(() -> parser.parse(read("error.xml"), "wuobs"))
                .isInstanceOf(XmlParseException.class)
                .hasMessageContaining("9999");
    }

    @Test
    void rejectsDoctype() {
        byte[] xml = """
                <?xml version="1.0"?>
                <!DOCTYPE RB [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <RB><Datas><RoomInfo Number1="&xxe;" Title2="1" /></Datas></RB>""".getBytes();

        assertThatThrownBy(() -> parser.parse(xml, "wuobs")).isInstanceOf(XmlParseException.class);
    }

    private static byte[] read(String name) throws IOException {
        try (InputStream in = XmlRoomStatusParserTest.class.getResourceAsStream("/xml/" + name)) {
            return in.readAllBytes();
        }
    }

}
