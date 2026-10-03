package com.everythingcanbe.linebotclinicnotifysystem.tracking;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.everythingcanbe.linebotclinicnotifysystem.domain.Subscriber;
import com.everythingcanbe.linebotclinicnotifysystem.repository.SubscriberRepository;

@Service
public class SubscriberService {

    private final SubscriberRepository subscriberRepository;
    private final Clock clock;

    public SubscriberService(SubscriberRepository subscriberRepository, Clock clock) {
        this.subscriberRepository = subscriberRepository;
        this.clock = clock;
    }

    @Transactional
    public Subscriber getOrCreate(String lineUserId) {
        return subscriberRepository.findByLineUserId(lineUserId)
                .orElseGet(() -> subscriberRepository.save(
                        new Subscriber(lineUserId, null, LocalDateTime.now(clock))));
    }

    @Transactional
    public Subscriber follow(String lineUserId, String displayName) {
        Subscriber subscriber = getOrCreate(lineUserId);
        subscriber.setFollowing(true);
        if (displayName != null) {
            subscriber.setDisplayName(displayName);
        }
        return subscriber;
    }

}
