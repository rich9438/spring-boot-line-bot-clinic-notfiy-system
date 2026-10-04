package com.everythingcanbe.linebotclinicnotifysystem.admin;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * 僅在 admin.enabled=true 時載入（預設關閉，前台不受影響）。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Documented
@ConditionalOnProperty(name = "admin.enabled", havingValue = "true")
public @interface ConditionalOnAdminEnabled {
}
