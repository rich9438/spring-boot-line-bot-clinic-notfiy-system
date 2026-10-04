package com.everythingcanbe.linebotclinicnotifysystem.admin.diagnostics;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.AppenderBase;

import com.everythingcanbe.linebotclinicnotifysystem.admin.ConditionalOnAdminEnabled;
import com.everythingcanbe.linebotclinicnotifysystem.config.ClinicProperties;

/**
 * 保留最近的 WARN／ERROR log 於記憶體，供後台「錯誤」「log」指令查詢（應用程式重啟後清空）。
 */
@Component
@ConditionalOnAdminEnabled
public class RecentLogBuffer extends AppenderBase<ILoggingEvent> {

    static final int CAPACITY = 500;

    private final Deque<LogEntry> entries = new ArrayDeque<>();
    private final ZoneId zoneId;

    public RecentLogBuffer(ClinicProperties properties) {
        this.zoneId = properties.zoneId();
    }

    @PostConstruct
    void attach() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        setContext(context);
        setName("ADMIN_RECENT_LOG");
        start();
        context.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(this);
    }

    @PreDestroy
    void detach() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.getLogger(Logger.ROOT_LOGGER_NAME).detachAppender(this);
        stop();
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (!event.getLevel().isGreaterOrEqual(Level.WARN)) {
            return;
        }
        String message = event.getFormattedMessage();
        IThrowableProxy throwable = event.getThrowableProxy();
        if (throwable != null) {
            message += " | " + throwable.getClassName() + ": " + throwable.getMessage();
        }
        LogEntry entry = new LogEntry(
                LocalDateTime.ofInstant(Instant.ofEpochMilli(event.getTimeStamp()), zoneId),
                event.getLevel().toString(),
                shortName(event.getLoggerName()),
                message);
        synchronized (entries) {
            if (entries.size() >= CAPACITY) {
                entries.removeFirst();
            }
            entries.addLast(entry);
        }
    }

    /**
     * @param keyword 篩選關鍵字（不分大小寫，比對 logger 與訊息），null 表示不篩選
     * @return 最新的在前
     */
    public List<LogEntry> recent(int limit, String keyword) {
        String needle = keyword == null ? null : keyword.toLowerCase(Locale.ROOT);
        List<LogEntry> result = new ArrayList<>();
        synchronized (entries) {
            var iterator = entries.descendingIterator();
            while (iterator.hasNext() && result.size() < limit) {
                LogEntry entry = iterator.next();
                if (needle == null || (entry.logger() + " " + entry.message()).toLowerCase(Locale.ROOT)
                        .contains(needle)) {
                    result.add(entry);
                }
            }
        }
        return result;
    }

    private static String shortName(String loggerName) {
        int index = loggerName.lastIndexOf('.');
        return index < 0 ? loggerName : loggerName.substring(index + 1);
    }

    public record LogEntry(LocalDateTime time, String level, String logger, String message) {
    }

}
