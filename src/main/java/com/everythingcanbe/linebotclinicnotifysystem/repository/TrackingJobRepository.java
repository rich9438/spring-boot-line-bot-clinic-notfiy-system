package com.everythingcanbe.linebotclinicnotifysystem.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.everythingcanbe.linebotclinicnotifysystem.domain.Subscriber;
import com.everythingcanbe.linebotclinicnotifysystem.domain.TrackingJob;

public interface TrackingJobRepository extends JpaRepository<TrackingJob, Long> {

    @EntityGraph(attributePaths = "subscriber")
    List<TrackingJob> findByProviderCodeAndRoomIdAndActiveTrue(String providerCode, Integer roomId);

    List<TrackingJob> findBySubscriberAndActiveTrue(Subscriber subscriber);

    List<TrackingJob> findByActiveTrue();

}
