package com.fhircare.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "doctors")
@Getter
@Setter
@NoArgsConstructor
public class Doctor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long doctorId;

    @Column(unique = true, nullable = false)
    private String fhirResourceId;

    @Column(unique = true, nullable = false)
    private String doctorCode;

    private Long userId;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    private String specialization;
    private String qualification;
    private String licenseNumber;
    private String phone;
    private String email;
    private Boolean active = true;

    private LocalDateTime createdAt = LocalDateTime.now();
}
