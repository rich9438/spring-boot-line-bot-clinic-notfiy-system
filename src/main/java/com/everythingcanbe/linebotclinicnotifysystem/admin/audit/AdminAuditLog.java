package com.everythingcanbe.linebotclinicnotifysystem.admin.audit;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 管理指令稽核紀錄。
 */
@Entity
@Table(name = "admin_audit_log")
public class AdminAuditLog {

    static final int MAX_COMMAND_LENGTH = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_line_user_id", nullable = false, length = 100)
    private String adminLineUserId;

    @Column(name = "command", nullable = false, length = MAX_COMMAND_LENGTH)
    private String command;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected AdminAuditLog() {
    }

    public AdminAuditLog(String adminLineUserId, String command, LocalDateTime createdAt) {
        this.adminLineUserId = adminLineUserId;
        this.command = command.length() > MAX_COMMAND_LENGTH ? command.substring(0, MAX_COMMAND_LENGTH) : command;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getAdminLineUserId() {
        return adminLineUserId;
    }

    public String getCommand() {
        return command;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

}
