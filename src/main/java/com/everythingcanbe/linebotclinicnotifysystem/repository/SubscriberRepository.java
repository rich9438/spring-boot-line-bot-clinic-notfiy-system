package com.everythingcanbe.linebotclinicnotifysystem.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.everythingcanbe.linebotclinicnotifysystem.domain.Subscriber;

public interface SubscriberRepository extends JpaRepository<Subscriber, Long> {

    Optional<Subscriber> findByLineUserId(String lineUserId);

    List<Subscriber> findTop6ByDisplayNameContainingIgnoreCaseOrderByCreatedAtDesc(String displayName);

    long countByFollowingTrue();

}
