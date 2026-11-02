package com.fhircare.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "fhir_resources", uniqueConstraints = @UniqueConstraint(columnNames = {"resource_type", "resource_id"}))
@Getter
@Setter
@NoArgsConstructor
public class FhirResource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String resourceType;

    @Column(nullable = false)
    private String resourceId;

    private Long patientId;
    private Integer versionNumber = 1;

    @Lob
    @Column(columnDefinition = "JSON", nullable = false)
    private String resourceJson;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();
}
