package com.fhircare.repository;

import com.fhircare.entity.Consent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConsentRepository extends JpaRepository<Consent, Long> {
    List<Consent> findByPatientId(Long patientId);
    List<Consent> findByDoctorIdAndStatus(Long doctorId, Consent.Status status);
    Optional<Consent> findTopByPatientIdAndDoctorIdOrderByConsentIdDesc(Long patientId, Long doctorId);
    boolean existsByPatientIdAndDoctorIdAndStatus(Long patientId, Long doctorId, Consent.Status status);
}
