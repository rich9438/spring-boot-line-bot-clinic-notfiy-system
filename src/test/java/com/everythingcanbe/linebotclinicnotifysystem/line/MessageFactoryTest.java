package com.everythingcanbe.linebotclinicnotifysystem.line;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linecorp.bot.jackson.ModelObjectMapper;
import com.linecorp.bot.messaging.model.Message;

import com.everythingcanbe.linebotclinicnotifysystem.config.ClinicProperties;
import com.everythingcanbe.linebotclinicnotifysystem.provider.RoomStatus;

class MessageFactoryTest {

    private final MessageFactory factory = new MessageFactory(new ClinicProperties("wuobs", "慈心吳婦產科",
            ZoneId.of("Asia/Taipei"), null, null, List.of(1, 2, 3)));

    private final ObjectMapper objectMapper = ModelObjectMapper.createNewObjectMapper();

    @Test
    void roomPickerHasPostbackButtonOnlyForRoomsInSession() throws Exception {
        Message picker = factory.roomPicker(List.of(
                new MessageFactory.RoomOption(1, Optional.of(status(1, "婦產科", 35, true))),
                new MessageFactory.RoomOption(2, Optional.of(status(2, "婦產科", 46, true))),
                new MessageFactory.RoomOption(3, Optional.of(status(3, "小兒科", null, false)))));

        String json = objectMapper.writeValueAsString(picker);

        assertThat(json)
                .contains("\"type\":\"flex\"", "\"type\":\"postback\"", "\"inputOption\":\"openKeyboard\"")
                .contains("action=select-room&room=1", "action=select-room&room=2")
                .doesNotContain("action=select-room&room=3")
                .contains("目前 46 號", "未看診");
    }

    private static RoomStatus status(int roomId, String department, Integer number, boolean inSession) {
        return new RoomStatus("wuobs", roomId, new String[] {"", "一診", "二診", "三診"}[roomId],
                inSession ? "吳瑞聰" : null, department, number, inSession, LocalDateTime.now());
    }

}
