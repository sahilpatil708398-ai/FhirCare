package com.fhircare.controller;

import com.fhircare.dto.StatusUpdateRequest;
import com.fhircare.entity.Consent;
import com.fhircare.entity.MedicalRecord;
import com.fhircare.entity.User;
import com.fhircare.repository.*;
import com.fhircare.service.AuditService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin
public class AdminController {

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final MedicalRecordRepository recordRepository;
    private final AccessRequestRepository accessRequestRepository;
    private final ConsentRepository consentRepository;
    private final FhirResourceRepository fhirResourceRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditService auditService;

    public AdminController(UserRepository userRepository, PatientRepository patientRepository,
                            DoctorRepository doctorRepository, MedicalRecordRepository recordRepository,
                            AccessRequestRepository accessRequestRepository, ConsentRepository consentRepository,
                            FhirResourceRepository fhirResourceRepository,
                            AuditLogRepository auditLogRepository, AuditService auditService) {
        this.userRepository = userRepository;
        this.patientRepository = patientRepository;
        this.doctorRepository = doctorRepository;
        this.recordRepository = recordRepository;
        this.accessRequestRepository = accessRequestRepository;
        this.consentRepository = consentRepository;
        this.fhirResourceRepository = fhirResourceRepository;
        this.auditLogRepository = auditLogRepository;
        this.auditService = auditService;
    }

    @GetMapping("/users")
    public List<User> users() {
        return userRepository.findAll();
    }

    @PutMapping("/users/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @RequestBody StatusUpdateRequest body) {
        return userRepository.findById(id).map(u -> {
            try {
                u.setStatus(User.Status.valueOf(body.getStatus().toUpperCase()));
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(Map.of("error", "status must be ACTIVE, INACTIVE or LOCKED"));
            }
            userRepository.save(u);
            auditService.log(id, "ADMIN", "USER_STATUS_CHANGE", "User", id.toString(), "Status set to " + u.getStatus());
            return ResponseEntity.ok(u);
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/audit-logs")
    public List<?> auditLogs() {
        return auditLogRepository.findAllByOrderByEventTimeDesc();
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        long activeConsents = consentRepository.findAll().stream()
                .filter(c -> c.getStatus() == Consent.Status.GRANTED).count();

        Map<String, Long> recordsByType = recordRepository.findAll().stream()
                .collect(Collectors.groupingBy(r -> r.getRecordType().name(), Collectors.counting()));

        return Map.of(
                "patients", patientRepository.count(),
                "doctors", doctorRepository.count(),
                "records", recordRepository.count(),
                "pendingRequests", accessRequestRepository.findAll().stream()
                        .filter(r -> r.getStatus().name().equals("PENDING")).count(),
                "activeConsents", activeConsents,
                "fhirResources", fhirResourceRepository.count(),
                "auditEvents", auditLogRepository.count(),
                "recordsByType", recordsByType
        );
    }
}
