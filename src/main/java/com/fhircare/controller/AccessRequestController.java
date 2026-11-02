package com.fhircare.controller;

import com.fhircare.dto.AccessDecisionRequest;
import com.fhircare.entity.AccessRequest;
import com.fhircare.entity.Consent;
import com.fhircare.entity.Patient;
import com.fhircare.repository.AccessRequestRepository;
import com.fhircare.repository.ConsentRepository;
import com.fhircare.repository.DoctorRepository;
import com.fhircare.repository.PatientRepository;
import com.fhircare.service.AuditService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;

/**
 * Implements Level-2 DFD "3.0 Request & View Patient Data":
 *   3.1 Check Patient Existence -> 3.2 Create Access Request -> 3.3 Get Consent -> 3.4 Fetch Patient Medical Data
 */
@RestController
@RequestMapping("/api/access-requests")
@CrossOrigin
public class AccessRequestController {

    private final AccessRequestRepository requestRepo;
    private final ConsentRepository consentRepo;
    private final PatientRepository patientRepo;
    private final DoctorRepository doctorRepo;
    private final AuditService auditService;

    public AccessRequestController(AccessRequestRepository requestRepo, ConsentRepository consentRepo,
                                    PatientRepository patientRepo, DoctorRepository doctorRepo,
                                    AuditService auditService) {
        this.requestRepo = requestRepo;
        this.consentRepo = consentRepo;
        this.patientRepo = patientRepo;
        this.doctorRepo = doctorRepo;
        this.auditService = auditService;
    }

    public record NewAccessRequest(String patientCode, Long requestingDoctorId, String reason) {}

    // 3.1 Check Patient Existence + 3.2 Create Access Request
    @PostMapping
    public ResponseEntity<?> create(@RequestBody NewAccessRequest body) {
        Patient patient = patientRepo.findByPatientCode(body.patientCode()).orElse(null);
        if (patient == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "No patient found with code " + body.patientCode()));
        }
        if (doctorRepo.findById(body.requestingDoctorId()).isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unknown requesting doctor"));
        }
        AccessRequest req = new AccessRequest();
        req.setPatientId(patient.getPatientId());
        req.setRequestingDoctorId(body.requestingDoctorId());
        req.setReason(body.reason());
        req = requestRepo.save(req);

        auditService.log(null, "DOCTOR", "REQUEST_ACCESS", "AccessRequest", String.valueOf(req.getRequestId()),
                "Doctor #" + body.requestingDoctorId() + " requested access to " + patient.getPatientCode());

        return ResponseEntity.status(HttpStatus.CREATED).body(req);
    }

    @GetMapping("/patient/{patientId}")
    public List<AccessRequest> byPatient(@PathVariable Long patientId) {
        return requestRepo.findByPatientIdOrderByRequestedAtDesc(patientId);
    }

    @GetMapping("/doctor/{doctorId}")
    public List<AccessRequest> byDoctor(@PathVariable Long doctorId) {
        return requestRepo.findByRequestingDoctorIdOrderByRequestedAtDesc(doctorId);
    }

    // 3.3 Get Consent (patient approves or rejects).
    // On APPROVED we do NOT grant real access yet — we generate a 6-digit
    // verification code. The patient reads/sends that code to the requesting
    // doctor out-of-band (in person, phone, SMS); the doctor must submit it
    // via /verify-code before a GRANTED consent is created (see below).
    @PutMapping("/{id}/decision")
    public ResponseEntity<?> decide(@PathVariable Long id, @RequestBody AccessDecisionRequest body) {
        var opt = requestRepo.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        AccessRequest req = opt.get();

        AccessRequest.Status decision;
        try {
            decision = AccessRequest.Status.valueOf(body.getDecision().toUpperCase());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "decision must be APPROVED or REJECTED"));
        }
        if (decision != AccessRequest.Status.APPROVED && decision != AccessRequest.Status.REJECTED) {
            return ResponseEntity.badRequest().body(Map.of("error", "decision must be APPROVED or REJECTED"));
        }

        req.setStatus(decision);
        req.setDecidedAt(LocalDateTime.now());

        if (decision == AccessRequest.Status.APPROVED) {
            req.setVerificationCode(generateCode());
            req.setCodeVerified(false);
            requestRepo.save(req);
            auditService.log(null, "PATIENT", "ACCESS_APPROVED_CODE_ISSUED", "AccessRequest", String.valueOf(req.getRequestId()),
                    "Patient approved access request #" + req.getRequestId() + " — verification code issued, pending doctor confirmation");
            return ResponseEntity.ok(Map.of("request", req,
                    "message", "Approved. Share the verification code with the doctor to activate access."));
        }

        requestRepo.save(req);
        Consent consent = new Consent();
        consent.setAccessRequestId(req.getRequestId());
        consent.setPatientId(req.getPatientId());
        consent.setDoctorId(req.getRequestingDoctorId());
        consent.setStatus(Consent.Status.DENIED);
        consentRepo.save(consent);

        auditService.log(null, "PATIENT", "CONSENT_DENIED", "AccessRequest", String.valueOf(req.getRequestId()),
                "Patient rejected access request #" + req.getRequestId());

        return ResponseEntity.ok(Map.of("request", req, "consent", consent));
    }

    public record VerifyCodeRequest(String code) {}

    // 3.4 Doctor submits the code the patient gave them. Only then is a
    // GRANTED consent created and real record access unlocked.
    @PutMapping("/{id}/verify-code")
    public ResponseEntity<?> verifyCode(@PathVariable Long id, @RequestBody VerifyCodeRequest body) {
        var opt = requestRepo.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        AccessRequest req = opt.get();

        if (req.getStatus() != AccessRequest.Status.APPROVED) {
            return ResponseEntity.badRequest().body(Map.of("error", "This request has not been approved by the patient yet."));
        }
        if (Boolean.TRUE.equals(req.getCodeVerified())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Access is already active for this request."));
        }
        if (body.code() == null || !body.code().trim().equals(req.getVerificationCode())) {
            auditService.log(null, "DOCTOR", "CODE_VERIFICATION_FAILED", "AccessRequest", String.valueOf(req.getRequestId()),
                    "Incorrect verification code entered for request #" + req.getRequestId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Incorrect verification code."));
        }

        req.setCodeVerified(true);
        req.setCodeVerifiedAt(LocalDateTime.now());
        requestRepo.save(req);

        Consent consent = new Consent();
        consent.setAccessRequestId(req.getRequestId());
        consent.setPatientId(req.getPatientId());
        consent.setDoctorId(req.getRequestingDoctorId());
        consent.setStatus(Consent.Status.GRANTED);
        consent.setGrantedAt(LocalDateTime.now());
        consentRepo.save(consent);

        auditService.log(null, "DOCTOR", "CONSENT_GRANTED", "AccessRequest", String.valueOf(req.getRequestId()),
                "Doctor #" + req.getRequestingDoctorId() + " verified code — access activated for request #" + req.getRequestId());

        return ResponseEntity.ok(Map.of("request", req, "consent", consent));
    }

    private String generateCode() {
        SecureRandom random = new SecureRandom();
        return String.format("%06d", random.nextInt(1_000_000));
    }

    // Revoke a previously granted consent
    @PutMapping("/consents/{consentId}/revoke")
    public ResponseEntity<?> revoke(@PathVariable Long consentId) {
        return consentRepo.findById(consentId).map(c -> {
            c.setStatus(Consent.Status.REVOKED);
            c.setRevokedAt(LocalDateTime.now());
            consentRepo.save(c);
            auditService.log(null, "PATIENT", "CONSENT_REVOKED", "Consent", String.valueOf(consentId), "Consent revoked");
            return ResponseEntity.ok(c);
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/consents/patient/{patientId}")
    public List<Consent> consentsForPatient(@PathVariable Long patientId) {
        return consentRepo.findByPatientId(patientId);
    }

    // All patients a doctor currently has GRANTED consent for — includes
    // consents that came from a verified access-request code AND patients
    // the doctor added directly (see PatientController.create).
    @GetMapping("/consents/doctor/{doctorId}")
    public List<Consent> consentsForDoctor(@PathVariable Long doctorId) {
        return consentRepo.findByDoctorIdAndStatus(doctorId, Consent.Status.GRANTED);
    }
}
