package com.everythingcanbe.linebotclinicnotifysystem.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.everythingcanbe.linebotclinicnotifysystem.domain.Subscriber;
import com.everythingcanbe.linebotclinicnotifysystem.domain.TrackingJob;

public interface TrackingJobRepository extends JpaRepository<TrackingJob, Long> {

    @EntityGraph(attributePaths = "subscriber")
    List<TrackingJob> findByProviderCodeAndRoomIdAndActiveTrue(String providerCode, Integer roomId);

    List<TrackingJob> findBySubscriberAndActiveTrue(Subscriber subscriber);

    List<TrackingJob> findByActiveTrue();

    List<TrackingJob> findTop5BySubscriberOrderByCreatedAtDesc(Subscriber subscriber);

    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(LocalDateTime from, LocalDateTime to);

    /** 各診追蹤中人數：[roomId, count] */
    @Query("select j.roomId, count(j) from TrackingJob j where j.active = true group by j.roomId")
    List<Object[]> countActiveByRoom();

    /** 期間內結束的任務，依原因分組：[endReason, count] */
    @Query("select j.endReason, count(j) from TrackingJob j "
            + "where j.endedAt >= :from and j.endedAt < :to group by j.endReason")
    List<Object[]> countEndedByReason(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

}
