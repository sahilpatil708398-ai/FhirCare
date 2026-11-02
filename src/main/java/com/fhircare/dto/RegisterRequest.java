package com.fhircare.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterRequest {
    private String username;
    private String email;
    private String password;
    private String fullName;
    private String role;       // PATIENT or DOCTOR
    private String gender;
    private String birthDate;  // yyyy-MM-dd, patients only
    private String specialization; // doctors only
}
