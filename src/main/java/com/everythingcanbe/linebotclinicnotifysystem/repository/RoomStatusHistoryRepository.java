package com.everythingcanbe.linebotclinicnotifysystem.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.everythingcanbe.linebotclinicnotifysystem.domain.RoomStatusHistory;

public interface RoomStatusHistoryRepository extends JpaRepository<RoomStatusHistory, Long> {

    List<RoomStatusHistory> findByProviderCodeAndRoomIdAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(
            String providerCode, Integer roomId, LocalDateTime since);

    Optional<RoomStatusHistory> findFirstByProviderCodeAndRoomIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
            String providerCode, Integer roomId, LocalDateTime since);

}
