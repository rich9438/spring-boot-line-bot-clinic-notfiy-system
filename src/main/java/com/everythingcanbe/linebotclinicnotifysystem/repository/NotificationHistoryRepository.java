package com.everythingcanbe.linebotclinicnotifysystem.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.everythingcanbe.linebotclinicnotifysystem.domain.NotificationHistory;
import com.everythingcanbe.linebotclinicnotifysystem.domain.TrackingJob;

public interface NotificationHistoryRepository extends JpaRepository<NotificationHistory, Long> {

    @Transactional
    @Modifying
    @Query("update NotificationHistory h set h.delivered = :delivered "
            + "where h.trackingJob.id = :jobId and h.threshold = :threshold")
    int updateDelivered(@Param("jobId") Long jobId, @Param("threshold") Integer threshold,
            @Param("delivered") boolean delivered);

    List<NotificationHistory> findTop10ByTrackingJobInAndPushedTrueOrderBySentAtDesc(List<TrackingJob> jobs);

    /** 期間內實際推播的筆數，依送達結果分組：[delivered, count] */
    @Query("select h.delivered, count(h) from NotificationHistory h "
            + "where h.pushed = true and h.sentAt >= :from and h.sentAt < :to group by h.delivered")
    List<Object[]> countPushedByDelivered(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("select h.threshold from NotificationHistory h where h.trackingJob = :job")
    Set<Integer> findThresholdsByTrackingJob(@Param("job") TrackingJob job);

}
