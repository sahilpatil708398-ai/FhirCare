package com.fhircare.repository;

import com.fhircare.entity.Patient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PatientRepository extends JpaRepository<Patient, Long> {
    Optional<Patient> findByPatientCode(String patientCode);
    Optional<Patient> findByFhirResourceId(String fhirResourceId);
    Optional<Patient> findByUserId(Long userId);
}
