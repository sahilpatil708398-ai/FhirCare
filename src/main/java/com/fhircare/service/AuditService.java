package com.fhircare.service;

import com.fhircare.entity.AuditLog;
import com.fhircare.repository.AuditLogRepository;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void log(Long userId, String userRole, String action, String resourceType, String resourceId, String description) {
        AuditLog log = new AuditLog();
        log.setUserId(userId);
        log.setUserRole(userRole);
        log.setAction(action);
        log.setResourceType(resourceType);
        log.setResourceId(resourceId);
        log.setDescription(description);
        auditLogRepository.save(log);
    }
}
