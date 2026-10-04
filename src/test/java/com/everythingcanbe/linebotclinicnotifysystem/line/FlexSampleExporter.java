package com.everythingcanbe.linebotclinicnotifysystem.line;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.linecorp.bot.jackson.ModelObjectMapper;
import com.linecorp.bot.messaging.model.Message;
import com.linecorp.bot.messaging.model.PushMessageRequest;

/**
 * 匯出所有卡片的 Push API request body。平常略過，需要時執行：
 * {@code ./mvnw test -Dtest=FlexSampleExporter -Dflex.samples.dir=deploy/flex-samples}
 */
class FlexSampleExporter {

    static final String USER_ID_PLACEHOLDER = "${LINE_USER_ID}";

    @Test
    void export() throws Exception {
        String dir = System.getProperty("flex.samples.dir");
        Assumptions.assumeTrue(dir != null, "未指定 -Dflex.samples.dir，略過匯出");

        ObjectMapper objectMapper = ModelObjectMapper.createNewObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT);
        Path output = Path.of(dir);
        Files.createDirectories(output);
        for (Map.Entry<String, List<Message>> sample : FlexSamples.all(FlexSamples.sampleFactory()).entrySet()) {
            // 以 SDK 實際送出的 PushMessageRequest 序列化，才會帶上每則訊息的 "type"
            PushMessageRequest body = new PushMessageRequest(USER_ID_PLACEHOLDER, sample.getValue(), null, null);
            Files.writeString(output.resolve(sample.getKey() + ".json"),
                    objectMapper.writeValueAsString(body) + "\n");
        }
    }

}
