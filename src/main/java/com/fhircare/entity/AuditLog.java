package com.fhircare.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long auditId;

    private Long userId;
    private String userRole;

    @Column(nullable = false)
    private String action;

    private String resourceType;
    private String resourceId;
    private String description;

    private LocalDateTime eventTime = LocalDateTime.now();
}
