package com.everythingcanbe.linebotclinicnotifysystem.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.everythingcanbe.linebotclinicnotifysystem.domain.Subscriber;
import com.everythingcanbe.linebotclinicnotifysystem.domain.SubscriberThreshold;

public interface SubscriberThresholdRepository extends JpaRepository<SubscriberThreshold, Long> {

    List<SubscriberThreshold> findBySubscriber(Subscriber subscriber);

    @Modifying(flushAutomatically = true)
    @Query("delete from SubscriberThreshold t where t.subscriber = :subscriber")
    void deleteBySubscriber(@Param("subscriber") Subscriber subscriber);

}
