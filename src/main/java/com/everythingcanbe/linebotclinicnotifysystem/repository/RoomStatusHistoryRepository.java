package com.everythingcanbe.linebotclinicnotifysystem.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.everythingcanbe.linebotclinicnotifysystem.domain.RoomStatusHistory;

public interface RoomStatusHistoryRepository extends JpaRepository<RoomStatusHistory, Long> {

    /** 期間內各診看診範圍：[roomId, 最早時間, 最晚時間, 最小號碼, 最大號碼] */
    @Query("select r.roomId, min(r.createdAt), max(r.createdAt), min(r.currentNumber), max(r.currentNumber) "
            + "from RoomStatusHistory r where r.createdAt >= :from and r.createdAt < :to "
            + "group by r.roomId order by r.roomId")
    List<Object[]> summarizeByRoom(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    List<RoomStatusHistory> findByProviderCodeAndRoomIdAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(
            String providerCode, Integer roomId, LocalDateTime since);

    Optional<RoomStatusHistory> findFirstByProviderCodeAndRoomIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
            String providerCode, Integer roomId, LocalDateTime since);

}
