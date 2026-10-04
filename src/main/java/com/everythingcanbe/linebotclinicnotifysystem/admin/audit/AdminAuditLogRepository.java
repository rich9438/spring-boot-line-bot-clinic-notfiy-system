package com.everythingcanbe.linebotclinicnotifysystem.admin.audit;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {
}
