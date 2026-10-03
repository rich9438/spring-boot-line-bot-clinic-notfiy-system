package com.everythingcanbe.linebotclinicnotifysystem.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 排程開關。測試環境以 clinic.polling.enabled=false 關閉，避免實際呼叫外部資料來源。
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "clinic.polling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
