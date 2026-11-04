package com.fhircare.controller;

import com.fhircare.entity.Consent;
import com.fhircare.entity.Doctor;
import com.fhircare.entity.Patient;
import com.fhircare.repository.ConsentRepository;
import com.fhircare.repository.DoctorRepository;
import com.fhircare.repository.PatientRepository;
import com.fhircare.service.AuditService;
import com.fhircare.service.FhirResourceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/patients")
@CrossOrigin
public class PatientController {

    private final PatientRepository repo;
    private final DoctorRepository doctorRepo;
    private final ConsentRepository consentRepo;
    private final FhirResourceService fhirResourceService;
    private final AuditService auditService;

    public PatientController(PatientRepository repo, DoctorRepository doctorRepo, ConsentRepository consentRepo,
                              FhirResourceService fhirResourceService, AuditService auditService) {
        this.repo = repo;
        this.doctorRepo = doctorRepo;
        this.consentRepo = consentRepo;
        this.fhirResourceService = fhirResourceService;
        this.auditService = auditService;
    }

    public record NewPatientRequest(String firstName, String lastName, String gender, String birthDate,
                                     String bloodGroup, String phone, String email, String address,
                                     String city, String state) {}

    public record DoctorUnlockRequest(Long doctorId) {}

    /**
     * A doctor manually adds a brand-new patient (no self-registration / login
     * needed for the patient). If createdByDoctorId is passed, that doctor is
     * automatically given a GRANTED consent for this patient — since they are
     * the one who just entered the patient's details, they don't need to go
     * through the access-request/verification-code flow for their own entry.
     * Any OTHER doctor who later wants access still has to request it and go
     * through the normal patient-approval + verification-code flow.
     */
    @PostMapping
    public ResponseEntity<?> create(@RequestBody NewPatientRequest body,
                                     @RequestParam(required = false) Long createdByDoctorId) {
        if (body.firstName() == null || body.firstName().isBlank() || body.lastName() == null || body.lastName().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "firstName and lastName are required"));
        }

        Patient p = new Patient();
        p.setFhirResourceId("Patient-" + UUID.randomUUID());
        p.setPatientCode(nextPatientCode());
        p.setFirstName(body.firstName().trim());
        p.setLastName(body.lastName().trim());
        p.setGender(body.gender());
        if (body.birthDate() != null && !body.birthDate().isBlank()) {
            p.setBirthDate(LocalDate.parse(body.birthDate()));
        }
        p.setBloodGroup(body.bloodGroup());
        p.setPhone(body.phone());
        p.setEmail(body.email());
        p.setAddress(body.address());
        p.setCity(body.city());
        p.setState(body.state());
        p.setActive(true);
        Patient saved = repo.save(p);
        fhirResourceService.generateForPatient(saved);

        String actorLabel = "A doctor";
        if (createdByDoctorId != null) {
            Doctor doc = doctorRepo.findById(createdByDoctorId).orElse(null);
            if (doc != null) {
                Consent consent = new Consent();
                consent.setPatientId(saved.getPatientId());
                consent.setDoctorId(createdByDoctorId);
                consent.setPurpose("Registering doctor");
                consent.setStatus(Consent.Status.GRANTED);
                consent.setGrantedAt(LocalDateTime.now());
                consentRepo.save(consent);
                actorLabel = "Dr. " + doc.getLastName();
            }
        }

        auditService.log(null, "DOCTOR", "ADD_PATIENT", "Patient", saved.getPatientCode(),
                actorLabel + " manually added new patient " + saved.getFirstName() + " " + saved.getLastName() +
                        " (" + saved.getPatientCode() + ")");

        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping
    public List<Patient> all() {
        return repo.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Patient> get(@PathVariable Long id) {
        return repo.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/code/{code}")
    public ResponseEntity<Patient> byCode(@PathVariable String code) {
        return repo.findByPatientCode(code).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/by-user/{userId}")
    public ResponseEntity<Patient> byUser(@PathVariable Long userId) {
        return repo.findByUserId(userId).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Patient> update(@PathVariable Long id, @Valid @RequestBody Patient incoming) {
        return repo.findById(id).map(existing -> {
            boolean alreadySubmitted = Boolean.TRUE.equals(existing.getProfileSubmitted());
            boolean doctorUpdate = incoming.getProfileEditUnlockedByDoctorId() != null;
            if (alreadySubmitted && !Boolean.TRUE.equals(existing.getProfileEditUnlocked())) {
                throw new IllegalStateException("Patient profile is locked after the one-time submission. Doctor permission is required.");
            }
            incoming.setPatientId(id);
            incoming.setFhirResourceId(existing.getFhirResourceId());
            incoming.setPatientCode(existing.getPatientCode());
            incoming.setCreatedAt(existing.getCreatedAt());
            incoming.setProfileSubmitted(true);
            incoming.setProfileSubmittedAt(existing.getProfileSubmittedAt() == null ? LocalDateTime.now() : existing.getProfileSubmittedAt());
            incoming.setProfileEditUnlocked(false);
            incoming.setProfileEditUnlockedByDoctorId(doctorUpdate ? incoming.getProfileEditUnlockedByDoctorId() : existing.getProfileEditUnlockedByDoctorId());
            incoming.setProfileEditUnlockedAt(doctorUpdate ? LocalDateTime.now() : existing.getProfileEditUnlockedAt());
            Patient saved = repo.save(incoming);
            fhirResourceService.generateForPatient(saved);
            auditService.log(existing.getUserId(), "PATIENT", "UPDATE_PROFILE", "Patient", saved.getPatientCode(), "Patient profile updated");
            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    /** Doctor-only workflow hook: unlock one profile edit, then the next save
     * consumes the unlock and immediately locks the profile again. */
    @PutMapping("/{id}/unlock-profile")
    public ResponseEntity<?> unlockProfile(@PathVariable Long id, @RequestBody DoctorUnlockRequest request) {
        if (request == null || request.doctorId() == null || doctorRepo.findById(request.doctorId()).isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "A valid doctorId is required."));
        }
        return repo.findById(id).map(patient -> {
            patient.setProfileEditUnlocked(true);
            patient.setProfileEditUnlockedByDoctorId(request.doctorId());
            patient.setProfileEditUnlockedAt(LocalDateTime.now());
            Patient saved = repo.save(patient);
            auditService.log(null, "DOCTOR", "UNLOCK_PATIENT_PROFILE", "Patient", saved.getPatientCode(),
                    "Doctor #" + request.doctorId() + " authorized one profile update");
            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!repo.existsById(id)) return ResponseEntity.notFound().build();
        repo.deleteById(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    /** Next free PAT-xxxx code (count()+1 breaks when codes have gaps, e.g. seeded data). */
    private String nextPatientCode() {
        long n = 1000 + repo.count() + 1;
        String code = "PAT-" + n;
        while (repo.findByPatientCode(code).isPresent()) {
            n++;
            code = "PAT-" + n;
        }
        return code;
    }
}