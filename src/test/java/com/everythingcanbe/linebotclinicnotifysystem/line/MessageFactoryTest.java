package com.everythingcanbe.linebotclinicnotifysystem.line;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linecorp.bot.jackson.ModelObjectMapper;
import com.linecorp.bot.messaging.model.FlexMessage;
import com.linecorp.bot.messaging.model.Message;

class MessageFactoryTest {

    private final ObjectMapper objectMapper = ModelObjectMapper.createNewObjectMapper();

    @Test
    void everyCardIsFlexWithinLineLimits() throws Exception {
        for (Map.Entry<String, List<Message>> sample : FlexSamples.all(FlexSamples.sampleFactory()).entrySet()) {
            assertThat(sample.getValue()).as(sample.getKey()).hasSizeBetween(1, 5);
            for (Message message : sample.getValue()) {
                assertThat(message).as(sample.getKey()).isInstanceOf(FlexMessage.class);
                FlexMessage flex = (FlexMessage) message;
                assertThat(flex.altText()).as(sample.getKey()).isNotBlank().hasSizeLessThanOrEqualTo(400);

                JsonNode contents = objectMapper.valueToTree(flex.contents());
                int bytes = objectMapper.writeValueAsString(contents).getBytes(StandardCharsets.UTF_8).length;
                if ("carousel".equals(contents.get("type").asText())) {
                    assertThat(contents.get("contents")).as(sample.getKey()).hasSizeLessThanOrEqualTo(12);
                    assertThat(bytes).as(sample.getKey()).isLessThan(50 * 1024);
                } else {
                    assertThat(bytes).as(sample.getKey()).isLessThan(30 * 1024);
                }
            }
        }
    }

    @Test
    void roomsCarouselHasOneCardPerRoomAndTrackButtonOnlyInSession() throws Exception {
        Message rooms = FlexSamples.all(FlexSamples.sampleFactory()).get("11-rooms").getFirst();

        JsonNode json = objectMapper.valueToTree(rooms);

        assertThat(json.at("/contents/type").asText()).isEqualTo("carousel");
        assertThat(json.at("/contents/contents")).hasSize(3);
        String text = json.toString();
        assertThat(text)
                .contains("\"type\":\"postback\"", "\"inputOption\":\"openKeyboard\"")
                .contains("action=select-room&room=1", "action=select-room&room=2")
                .doesNotContain("action=select-room&room=3")
                .contains("未看診", "小兒科");
        assertThat(json.at("/contents/contents/2/footer").isMissingNode()).isTrue();
    }

    @Test
    void progressCardShowsRemainingAndEta() throws Exception {
        String json = objectMapper.writeValueAsString(FlexSamples.all(FlexSamples.sampleFactory()).get("04-progress").getFirst());

        assertThat(json).contains("即將輪到您看診", "目前叫號", "53", "56", "3 位", "約 8 分鐘", "#E67E22");
    }

    @Test
    void thresholdSettingsCardHasFillInAndResetButtons() throws Exception {
        String json = objectMapper.writeValueAsString(FlexSamples.all(FlexSamples.sampleFactory()).get("13-threshold-settings").getFirst());

        assertThat(json)
                .contains("通知設定", "自訂", "剩 15 位", "剩 8 位", "剩 3 位", "到號")
                .contains("\"inputOption\":\"openKeyboard\"", "\"fillInText\":\"設定門檻 \"",
                        "action=custom-thresholds")
                .contains("\"text\":\"重設門檻\"", "恢復為 10、3、到號");
    }

    @Test
    void describeThresholds() {
        assertThat(MessageFactory.describeThresholds(List.of(10, 3, 0))).isEqualTo("剩 10、3 位及到號時");
        assertThat(MessageFactory.describeThresholds(List.of(0))).isEqualTo("到號時");
    }

}
