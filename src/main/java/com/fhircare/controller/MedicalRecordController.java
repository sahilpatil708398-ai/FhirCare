package com.fhircare.controller;

import com.fhircare.entity.Consent;
import com.fhircare.entity.MedicalRecord;
import com.fhircare.entity.Patient;
import com.fhircare.repository.ConsentRepository;
import com.fhircare.repository.MedicalRecordRepository;
import com.fhircare.repository.PatientRepository;
import com.fhircare.service.AuditService;
import com.fhircare.service.FhirResourceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/records")
@CrossOrigin
public class MedicalRecordController {

    private final MedicalRecordRepository recordRepo;
    private final PatientRepository patientRepo;
    private final ConsentRepository consentRepo;
    private final FhirResourceService fhirResourceService;
    private final AuditService auditService;

    public MedicalRecordController(MedicalRecordRepository recordRepo, PatientRepository patientRepo,
                                    ConsentRepository consentRepo, FhirResourceService fhirResourceService,
                                    AuditService auditService) {
        this.recordRepo = recordRepo;
        this.patientRepo = patientRepo;
        this.consentRepo = consentRepo;
        this.fhirResourceService = fhirResourceService;
        this.auditService = auditService;
    }

    /**
     * View a patient's medical records.
     * The patient themselves always has access. A doctor may view only if they
     * hold a GRANTED consent for that patient (Level-2 DFD: 3.3 Get Consent -> 3.4 Fetch Patient Medical Data).
     */
    @GetMapping("/patient/{patientId}")
    public ResponseEntity<?> byPatient(@PathVariable Long patientId,
                                        @RequestParam(required = false) Long doctorId) {
        Patient patient = patientRepo.findById(patientId).orElse(null);
        if (patient == null) return ResponseEntity.notFound().build();

        if (doctorId != null) {
            boolean granted = consentRepo.existsByPatientIdAndDoctorIdAndStatus(patientId, doctorId, Consent.Status.GRANTED);
            if (!granted) {
                auditService.log(null, "DOCTOR", "ACCESS_DENIED", "MedicalRecord", patient.getPatientCode(),
                        "Doctor #" + doctorId + " tried to view records without consent");
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "No approved consent for this patient yet. Send an access request first."));
            }
            auditService.log(null, "DOCTOR", "VIEW_RECORDS", "MedicalRecord", patient.getPatientCode(),
                    "Doctor #" + doctorId + " viewed records (consented)");
        }
        return ResponseEntity.ok(recordRepo.findByPatientIdOrderByRecordDateDesc(patientId));
    }

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody MedicalRecord incoming,
                                     @RequestParam(required = false) Long actingDoctorId) {
        Patient patient = patientRepo.findById(incoming.getPatientId()).orElse(null);
        if (patient == null) return ResponseEntity.badRequest().body(Map.of("error", "Unknown patientId"));

        if (actingDoctorId != null) {
            boolean granted = consentRepo.existsByPatientIdAndDoctorIdAndStatus(incoming.getPatientId(), actingDoctorId, Consent.Status.GRANTED);
            if (!granted) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "You need an approved consent from this patient before adding records."));
            }
            incoming.setDoctorId(actingDoctorId);
        }

        incoming.setFhirResourceId(prefixFor(incoming.getRecordType()) + "-" + UUID.randomUUID());
        MedicalRecord saved = recordRepo.save(incoming);

        fhirResourceService.generateForPatient(patient);
        auditService.log(null, "DOCTOR", "ADD_RECORD", "MedicalRecord", saved.getFhirResourceId(),
                "Added " + saved.getRecordType() + " for patient " + patient.getPatientCode());

        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody MedicalRecord incoming) {
        return recordRepo.findById(id).map(existing -> {
            if (incoming.getDoctorId() == null || !consentRepo.existsByPatientIdAndDoctorIdAndStatus(existing.getPatientId(), incoming.getDoctorId(), Consent.Status.GRANTED)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only an authorized doctor can change medical records."));
            }
            incoming.setRecordId(id);
            incoming.setFhirResourceId(existing.getFhirResourceId());
            MedicalRecord saved = recordRepo.save(incoming);
            patientRepo.findById(saved.getPatientId()).ifPresent(fhirResourceService::generateForPatient);
            auditService.log(null, "DOCTOR", "UPDATE_RECORD", "MedicalRecord", saved.getFhirResourceId(), "Record updated");
            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        var existing = recordRepo.findById(id);
        if (existing.isEmpty()) return ResponseEntity.notFound().build();
        recordRepo.deleteById(id);
        patientRepo.findById(existing.get().getPatientId()).ifPresent(fhirResourceService::generateForPatient);
        return ResponseEntity.noContent().build();
    }

    private String prefixFor(MedicalRecord.RecordType type) {
        return switch (type) {
            case CONDITION -> "Condition";
            case OBSERVATION -> "Observation";
            case MEDICATION -> "MedicationRequest";
            case PROCEDURE -> "Procedure";
            case REPORT -> "DiagnosticReport";
            case IMMUNIZATION -> "Immunization";
            case ALLERGY -> "AllergyIntolerance";
        };
    }
}
