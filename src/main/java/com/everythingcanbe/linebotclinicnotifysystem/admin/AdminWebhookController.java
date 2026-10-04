package com.everythingcanbe.linebotclinicnotifysystem.admin;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.linecorp.bot.parser.LineSignatureValidator;
import com.linecorp.bot.parser.WebhookParser;
import com.linecorp.bot.webhook.model.CallbackRequest;
import com.linecorp.bot.webhook.model.Event;

/**
 * 後台 Channel 的 Webhook（POST /admin/callback）。LINE SDK 的自動設定只服務前台，
 * 這裡以後台 Channel Secret 自行驗證簽章，事件交由背景執行緒處理以便立即回應 200。
 */
@RestController
@ConditionalOnAdminEnabled
public class AdminWebhookController {

    public static final String PATH = "/admin/callback";

    private static final Logger log = LoggerFactory.getLogger(AdminWebhookController.class);

    private final WebhookParser parser;
    private final AdminEventHandler eventHandler;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public AdminWebhookController(AdminProperties properties, AdminEventHandler eventHandler) {
        this.parser = new WebhookParser(
                new LineSignatureValidator(properties.channelSecret().getBytes(StandardCharsets.UTF_8)));
        this.eventHandler = eventHandler;
    }

    @PostMapping(PATH)
    public ResponseEntity<Void> callback(
            @RequestHeader(value = WebhookParser.SIGNATURE_HEADER_NAME, required = false) String signature,
            @RequestBody byte[] body) {
        CallbackRequest request;
        try {
            request = parser.handle(signature, body);
        } catch (Exception e) {
            log.warn("Rejected admin webhook: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
        for (Event event : request.events()) {
            executor.execute(() -> eventHandler.handle(event));
        }
        return ResponseEntity.ok().build();
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }

}
