package com.fhircare.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "access_requests")
@Getter
@Setter
@NoArgsConstructor
public class AccessRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long requestId;

    @Column(nullable = false)
    private Long patientId;

    @Column(nullable = false)
    private Long requestingDoctorId;

    private String reason;

    @Enumerated(EnumType.STRING)
    private Status status = Status.PENDING;

    private LocalDateTime requestedAt = LocalDateTime.now();
    private LocalDateTime decidedAt;

    // 6-digit code generated when the patient approves. The patient reads this
    // code out to the requesting doctor; the doctor must submit it back before
    // a GRANTED consent (and real record access) is created.
    private String verificationCode;
    private LocalDateTime codeVerifiedAt;
    private Boolean codeVerified = false;

    public enum Status { PENDING, APPROVED, REJECTED, REVOKED }
}
