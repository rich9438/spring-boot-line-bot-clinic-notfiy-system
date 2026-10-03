package com.everythingcanbe.linebotclinicnotifysystem.config;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * 通知規則的系統預設值（notification.*）。使用者自訂門檻存於資料庫。
 */
@Validated
@ConfigurationProperties("notification")
public record NotificationProperties(
        @NotEmpty @DefaultValue({"10", "3", "0"}) List<@PositiveOrZero Integer> defaultThresholds,
        @Positive @DefaultValue("10") int sessionResetDrop,
        @Valid @DefaultValue Eta eta) {

    public record Eta(
            @Positive @DefaultValue("60") int windowMinutes,
            @Positive @DefaultValue("3") int minAdvance) {
    }

}
