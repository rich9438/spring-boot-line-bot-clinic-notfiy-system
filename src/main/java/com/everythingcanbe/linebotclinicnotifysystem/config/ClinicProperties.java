package com.everythingcanbe.linebotclinicnotifysystem.config;

import java.time.ZoneId;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * 診所與輪詢相關的系統設定（clinic.*）。
 */
@Validated
@ConfigurationProperties("clinic")
public record ClinicProperties(
        @NotBlank @DefaultValue("wuobs") String provider,
        @NotBlank @DefaultValue("慈心吳婦產科") String name,
        @NotNull @DefaultValue("Asia/Taipei") ZoneId zoneId,
        @Valid @DefaultValue Polling polling,
        @Valid @DefaultValue WuObs wuobs,
        @NotEmpty List<@Positive Integer> rooms) {

    public record Polling(
            @DefaultValue("true") boolean enabled,
            @Positive @DefaultValue("30") int intervalSeconds) {
    }

    public record WuObs(
            @NotBlank
            @DefaultValue("https://s3-ap-southeast-1.amazonaws.com/charity-wuobs/MedicineNumberList%02d.xml")
            String urlTemplate) {
    }

}
