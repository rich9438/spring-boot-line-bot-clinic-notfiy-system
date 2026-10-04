package com.everythingcanbe.linebotclinicnotifysystem.notification;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.everythingcanbe.linebotclinicnotifysystem.config.NotificationProperties;
import com.everythingcanbe.linebotclinicnotifysystem.domain.Subscriber;
import com.everythingcanbe.linebotclinicnotifysystem.domain.SubscriberThreshold;
import com.everythingcanbe.linebotclinicnotifysystem.repository.SubscriberThresholdRepository;

/**
 * 取得使用者的有效通知門檻：有自訂門檻用自訂，否則用系統預設。結果由大到小且必含 0（到號）。
 */
@Component
public class ThresholdResolver {

    private final SubscriberThresholdRepository thresholdRepository;
    private final NotificationProperties properties;

    public ThresholdResolver(SubscriberThresholdRepository thresholdRepository, NotificationProperties properties) {
        this.thresholdRepository = thresholdRepository;
        this.properties = properties;
    }

    public List<Integer> resolve(Subscriber subscriber) {
        List<Integer> custom = thresholdRepository.findBySubscriber(subscriber).stream()
                .map(SubscriberThreshold::getThreshold)
                .toList();
        return normalize(custom.isEmpty() ? properties.defaultThresholds() : custom);
    }

    public boolean hasCustom(Subscriber subscriber) {
        return !thresholdRepository.findBySubscriber(subscriber).isEmpty();
    }

    public List<Integer> defaults() {
        return normalize(properties.defaultThresholds());
    }

    public static List<Integer> normalize(Collection<Integer> thresholds) {
        return Stream.concat(thresholds.stream(), Stream.of(0))
                .filter(threshold -> threshold >= 0)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .toList();
    }

}
