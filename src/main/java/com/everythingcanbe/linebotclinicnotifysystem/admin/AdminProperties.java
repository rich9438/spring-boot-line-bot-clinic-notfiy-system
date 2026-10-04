package com.everythingcanbe.linebotclinicnotifysystem.admin;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 後台管理 Bot 設定（admin.*）。後台為同一 Provider 下的另一個 Channel，管理者的 userId 與前台相同。
 *
 * @param userIds 管理者白名單（LINE userId）
 */
@ConfigurationProperties("admin")
public record AdminProperties(
        @DefaultValue("false") boolean enabled,
        String channelToken,
        String channelSecret,
        @DefaultValue List<String> userIds,
        @DefaultValue Alerts alerts) {

    /**
     * @param fetchFailureThreshold 單一診間連續抓取失敗幾次後告警
     * @param throttle              同一類告警的最短間隔
     * @param quotaWarnRatio        推播額度用量達此比例時預警
     */
    public record Alerts(
            @DefaultValue("5") int fetchFailureThreshold,
            @DefaultValue("30m") Duration throttle,
            @DefaultValue("0.8") double quotaWarnRatio) {
    }

    public List<String> adminIds() {
        return userIds.stream().map(String::trim).filter(id -> !id.isEmpty()).distinct().toList();
    }

    public boolean isAdmin(String lineUserId) {
        return lineUserId != null && adminIds().contains(lineUserId);
    }

}
