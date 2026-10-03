package com.everythingcanbe.linebotclinicnotifysystem.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 使用者自訂通知門檻（notification_rule 表）。
 */
@Entity
@Table(name = "notification_rule")
public class SubscriberThreshold {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscriber_id", nullable = false)
    private Subscriber subscriber;

    @Column(name = "threshold", nullable = false)
    private Integer threshold;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected SubscriberThreshold() {
    }

    public SubscriberThreshold(Subscriber subscriber, Integer threshold, LocalDateTime createdAt) {
        this.subscriber = subscriber;
        this.threshold = threshold;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Subscriber getSubscriber() {
        return subscriber;
    }

    public Integer getThreshold() {
        return threshold;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

}
