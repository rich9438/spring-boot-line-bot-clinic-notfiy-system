package com.everythingcanbe.linebotclinicnotifysystem.line;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.linecorp.bot.client.base.Result;
import com.linecorp.bot.messaging.client.MessagingApiClient;
import com.linecorp.bot.messaging.model.ReplyMessageRequest;
import com.linecorp.bot.messaging.model.TextMessage;

import com.everythingcanbe.linebotclinicnotifysystem.provider.ClinicProvider;

/**
 * 驗證 LINE SDK 與 Spring Boot 4 的整合：簽章驗證、事件分派、Reply。
 */
@SpringBootTest
@AutoConfigureMockMvc
class LineWebhookIntegrationTest {

    private static final String CHANNEL_SECRET = "test-secret";

    @MockitoBean
    MessagingApiClient messagingApiClient;

    @MockitoBean
    ClinicProvider provider;

    @Autowired
    MockMvc mockMvc;

    @Test
    void textMessageIsAnsweredByReply() throws Exception {
        when(messagingApiClient.replyMessage(any()))
                .thenReturn(CompletableFuture.completedFuture(new Result<>(null, null, null)));

        String body = webhookBody("幫助", "user");
        mockMvc.perform(post("/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Line-Signature", sign(body))
                        .content(body))
                .andExpect(status().isOk());

        ArgumentCaptor<ReplyMessageRequest> captor = ArgumentCaptor.forClass(ReplyMessageRequest.class);
        verify(messagingApiClient, timeout(2000)).replyMessage(captor.capture());
        assertThat(captor.getValue().replyToken()).isEqualTo("reply-token");
        assertThat(captor.getValue().messages()).singleElement()
                .extracting(m -> ((TextMessage) m).text()).asString().contains("支援的指令");
    }

    @Test
    void groupMessageIsIgnored() throws Exception {
        String body = webhookBody("幫助", "group");
        mockMvc.perform(post("/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Line-Signature", sign(body))
                        .content(body))
                .andExpect(status().isOk());

        verify(messagingApiClient, never()).replyMessage(any());
    }

    @Test
    void invalidSignatureIsRejected() throws Exception {
        String body = webhookBody("幫助", "user");
        mockMvc.perform(post("/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Line-Signature", "invalid")
                        .content(body))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isGreaterThanOrEqualTo(400));

        verify(messagingApiClient, never()).replyMessage(any());
    }

    private static String webhookBody(String text, String sourceType) {
        String source = sourceType.equals("group")
                ? "{\"type\":\"group\",\"groupId\":\"G1\",\"userId\":\"U1\"}"
                : "{\"type\":\"user\",\"userId\":\"U1\"}";
        return """
                {"destination":"Uxxxx","events":[{"type":"message","mode":"active","timestamp":1759470000000,
                "webhookEventId":"01H000","deliveryContext":{"isRedelivery":false},
                "source":%s,"replyToken":"reply-token",
                "message":{"type":"text","id":"1","quoteToken":"q","text":"%s"}}]}""".formatted(source, text);
    }

    private static String sign(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(CHANNEL_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

}
