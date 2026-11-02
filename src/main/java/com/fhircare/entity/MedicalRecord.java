package com.fhircare.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "medical_records")
@Getter
@Setter
@NoArgsConstructor
public class MedicalRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long recordId;

    @Column(unique = true, nullable = false)
    private String fhirResourceId;

    @Column(nullable = false)
    private Long patientId;

    private Long doctorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecordType recordType;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String valueText;
    private String unit;
    private String status = "ACTIVE";
    private LocalDate recordDate;

    private LocalDateTime createdAt = LocalDateTime.now();

    public enum RecordType { CONDITION, OBSERVATION, MEDICATION, PROCEDURE, REPORT, IMMUNIZATION, ALLERGY }
}
