package com.fhircare.controller;

import com.fhircare.entity.FhirResource;
import com.fhircare.entity.Patient;
import com.fhircare.repository.PatientRepository;
import com.fhircare.service.AuditService;
import com.fhircare.service.FhirResourceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Process 5.0 "Generate FHIR Resources" in the DFD: turns Patient + Medical
 * Record rows into an R5-shaped JSON Bundle and stores it in D5.
 */
@RestController
@RequestMapping("/api/fhir")
@CrossOrigin
public class FhirController {

    private final PatientRepository patientRepository;
    private final FhirResourceService fhirResourceService;
    private final AuditService auditService;

    public FhirController(PatientRepository patientRepository, FhirResourceService fhirResourceService, AuditService auditService) {
        this.patientRepository = patientRepository;
        this.fhirResourceService = fhirResourceService;
        this.auditService = auditService;
    }

    @GetMapping("/patient/{patientCode}")
    public ResponseEntity<?> getBundle(@PathVariable String patientCode) {
        Patient patient = patientRepository.findByPatientCode(patientCode).orElse(null);
        if (patient == null) return ResponseEntity.notFound().build();
        FhirResource resource = fhirResourceService.generateForPatient(patient);
        auditService.log(null, null, "GENERATE_FHIR", "Bundle", patient.getFhirResourceId(),
                "FHIR bundle generated for " + patientCode);
        return ResponseEntity.ok(resource);
    }
}
