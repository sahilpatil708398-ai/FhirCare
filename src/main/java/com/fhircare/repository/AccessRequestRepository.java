package com.fhircare.repository;

import com.fhircare.entity.AccessRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccessRequestRepository extends JpaRepository<AccessRequest, Long> {
    List<AccessRequest> findByPatientIdOrderByRequestedAtDesc(Long patientId);
    List<AccessRequest> findByRequestingDoctorIdOrderByRequestedAtDesc(Long doctorId);
}
