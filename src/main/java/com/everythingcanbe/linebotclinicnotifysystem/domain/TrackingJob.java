package com.everythingcanbe.linebotclinicnotifysystem.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "tracking_job")
public class TrackingJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscriber_id", nullable = false)
    private Subscriber subscriber;

    @Column(name = "provider_code", nullable = false, length = 30)
    private String providerCode;

    @Column(name = "room_id", nullable = false)
    private Integer roomId;

    @Column(name = "target_number", nullable = false)
    private Integer targetNumber;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Enumerated(EnumType.STRING)
    @Column(name = "end_reason", length = 20)
    private EndReason endReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    protected TrackingJob() {
    }

    public TrackingJob(Subscriber subscriber, String providerCode, Integer roomId, Integer targetNumber,
            LocalDateTime createdAt) {
        this.subscriber = subscriber;
        this.providerCode = providerCode;
        this.roomId = roomId;
        this.targetNumber = targetNumber;
        this.active = true;
        this.createdAt = createdAt;
    }

    public void end(EndReason reason, LocalDateTime endedAt) {
        this.active = false;
        this.endReason = reason;
        this.endedAt = endedAt;
    }

    public Long getId() {
        return id;
    }

    public Subscriber getSubscriber() {
        return subscriber;
    }

    public String getProviderCode() {
        return providerCode;
    }

    public Integer getRoomId() {
        return roomId;
    }

    public Integer getTargetNumber() {
        return targetNumber;
    }

    public boolean isActive() {
        return active;
    }

    public EndReason getEndReason() {
        return endReason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getEndedAt() {
        return endedAt;
    }

}
