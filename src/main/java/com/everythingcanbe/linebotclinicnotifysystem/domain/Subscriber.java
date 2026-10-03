package com.everythingcanbe.linebotclinicnotifysystem.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "subscriber")
public class Subscriber {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "line_user_id", nullable = false, unique = true, length = 100)
    private String lineUserId;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(name = "following", nullable = false)
    private boolean following;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected Subscriber() {
    }

    public Subscriber(String lineUserId, String displayName, LocalDateTime createdAt) {
        this.lineUserId = lineUserId;
        this.displayName = displayName;
        this.following = true;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getLineUserId() {
        return lineUserId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public boolean isFollowing() {
        return following;
    }

    public void setFollowing(boolean following) {
        this.following = following;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

}
