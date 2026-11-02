package com.fhircare.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class AuthResponse {
    private Long userId;
    private String username;
    private String fullName;
    private String role;
    private Long profileId;     // patientId or doctorId
    private String profileCode; // patientCode or doctorCode
}
