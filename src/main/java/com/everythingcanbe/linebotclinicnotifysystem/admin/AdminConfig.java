package com.everythingcanbe.linebotclinicnotifysystem.admin;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.linecorp.bot.messaging.client.MessagingApiClient;

@Configuration(proxyBeanMethods = false)
@ConditionalOnAdminEnabled
public class AdminConfig {

    /**
     * 後台 Channel 的 client 包在 AdminMessenger 內，不註冊成 MessagingApiClient bean，
     * 以免 LINE SDK 的自動設定（@ConditionalOnMissingBean）因此不建立前台 client。
     */
    @Bean
    AdminMessenger adminMessenger(AdminProperties properties) {
        if (isBlank(properties.channelToken()) || isBlank(properties.channelSecret())) {
            throw new IllegalStateException(
                    "admin.enabled=true 但未設定 ADMIN_LINE_CHANNEL_TOKEN / ADMIN_LINE_CHANNEL_SECRET");
        }
        return new AdminMessenger(MessagingApiClient.builder(properties.channelToken()).build(), properties);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

}
