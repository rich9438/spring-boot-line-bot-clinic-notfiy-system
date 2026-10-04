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
 * 已處理的通知門檻；pushed=false 代表靜默略過（未實際推播）。
 */
@Entity
@Table(name = "notification_history")
public class NotificationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tracking_job_id", nullable = false)
    private TrackingJob trackingJob;

    @Column(name = "threshold", nullable = false)
    private Integer threshold;

    @Column(name = "pushed", nullable = false)
    private boolean pushed;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    /** 推播結果：true 送達、false 失敗、null 未推播或尚未記錄 */
    @Column(name = "delivered")
    private Boolean delivered;

    protected NotificationHistory() {
    }

    public NotificationHistory(TrackingJob trackingJob, Integer threshold, boolean pushed, LocalDateTime sentAt) {
        this.trackingJob = trackingJob;
        this.threshold = threshold;
        this.pushed = pushed;
        this.sentAt = sentAt;
    }

    public Long getId() {
        return id;
    }

    public TrackingJob getTrackingJob() {
        return trackingJob;
    }

    public Integer getThreshold() {
        return threshold;
    }

    public boolean isPushed() {
        return pushed;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public Boolean getDelivered() {
        return delivered;
    }

}
