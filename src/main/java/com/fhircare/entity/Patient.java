package com.fhircare.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "patients")
@Getter
@Setter
@NoArgsConstructor
public class Patient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long patientId;

    @Column(unique = true, nullable = false)
    private String fhirResourceId;

    @Column(unique = true, nullable = false)
    private String patientCode;

    private Long userId;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    private String gender;
    private LocalDate birthDate;
    private String bloodGroup;
    private String phone;
    private String email;

    @Column(columnDefinition = "TEXT")
    private String address;

    private String city;
    private String state;
    private Boolean active = true;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    /** Patient may submit personal details exactly once. After submission,
     * edits require a doctor-approved unlock request. */
    private Boolean profileSubmitted = false;
    private LocalDateTime profileSubmittedAt;
    private Boolean profileEditUnlocked = false;
    private Long profileEditUnlockedByDoctorId;
    private LocalDateTime profileEditUnlockedAt;
}
