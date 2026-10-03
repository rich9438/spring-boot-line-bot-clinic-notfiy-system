package com.everythingcanbe.linebotclinicnotifysystem.repository;

import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.everythingcanbe.linebotclinicnotifysystem.domain.NotificationHistory;
import com.everythingcanbe.linebotclinicnotifysystem.domain.TrackingJob;

public interface NotificationHistoryRepository extends JpaRepository<NotificationHistory, Long> {

    @Query("select h.threshold from NotificationHistory h where h.trackingJob = :job")
    Set<Integer> findThresholdsByTrackingJob(@Param("job") TrackingJob job);

}
