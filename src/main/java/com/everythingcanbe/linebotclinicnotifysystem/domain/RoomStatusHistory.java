package com.everythingcanbe.linebotclinicnotifysystem.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 看診號碼變動紀錄，用於候診時間預估與統計。
 */
@Entity
@Table(name = "room_status_history")
public class RoomStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider_code", nullable = false, length = 30)
    private String providerCode;

    @Column(name = "room_id", nullable = false)
    private Integer roomId;

    @Column(name = "current_number", nullable = false)
    private Integer currentNumber;

    @Column(name = "doctor_name", length = 50)
    private String doctorName;

    @Column(name = "department", length = 50)
    private String department;

    @Column(name = "exec_time")
    private LocalDateTime execTime;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected RoomStatusHistory() {
    }

    public RoomStatusHistory(String providerCode, Integer roomId, Integer currentNumber, String doctorName,
            String department, LocalDateTime execTime, LocalDateTime createdAt) {
        this.providerCode = providerCode;
        this.roomId = roomId;
        this.currentNumber = currentNumber;
        this.doctorName = doctorName;
        this.department = department;
        this.execTime = execTime;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getProviderCode() {
        return providerCode;
    }

    public Integer getRoomId() {
        return roomId;
    }

    public Integer getCurrentNumber() {
        return currentNumber;
    }

    public String getDoctorName() {
        return doctorName;
    }

    public String getDepartment() {
        return department;
    }

    public LocalDateTime getExecTime() {
        return execTime;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

}
