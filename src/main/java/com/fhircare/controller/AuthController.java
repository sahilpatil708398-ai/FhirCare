package com.fhircare.controller;

import com.fhircare.dto.AuthResponse;
import com.fhircare.dto.LoginRequest;
import com.fhircare.dto.RegisterRequest;
import com.fhircare.entity.Doctor;
import com.fhircare.entity.Patient;
import com.fhircare.entity.User;
import com.fhircare.repository.DoctorRepository;
import com.fhircare.repository.PatientRepository;
import com.fhircare.repository.UserRepository;
import com.fhircare.service.AuditService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin
public class AuthController {

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public AuthController(UserRepository userRepository, PatientRepository patientRepository,
                           DoctorRepository doctorRepository, PasswordEncoder passwordEncoder,
                           AuditService auditService) {
        this.userRepository = userRepository;
        this.patientRepository = patientRepository;
        this.doctorRepository = doctorRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest req) {
        if (req.getUsername() == null || req.getPassword() == null || req.getRole() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "username, password and role are required"));
        }
        if (req.getEmail() == null || req.getEmail().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "email is required"));
        }
        if (userRepository.existsByUsername(req.getUsername())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Username already taken"));
        }
        if (userRepository.existsByEmail(req.getEmail())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Email already registered"));
        }

        User.Role role;
        try {
            role = User.Role.valueOf(req.getRole().toUpperCase());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "role must be PATIENT or DOCTOR"));
        }
        if (role == User.Role.ADMIN) {
            return ResponseEntity.badRequest().body(Map.of("error", "Admin accounts cannot self-register"));
        }

        User user = new User();
        user.setUsername(req.getUsername());
        user.setEmail(req.getEmail());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setFullName(req.getFullName());
        user.setRole(role);
        user = userRepository.save(user);

        Long profileId = null;
        String profileCode = null;

        if (role == User.Role.PATIENT) {
            Patient p = new Patient();
            p.setUserId(user.getUserId());
            p.setFhirResourceId("Patient-" + UUID.randomUUID());
            p.setPatientCode(nextPatientCode());
            String[] names = splitName(req.getFullName());
            p.setFirstName(names[0]);
            p.setLastName(names[1]);
            p.setGender(req.getGender());
            if (req.getBirthDate() != null && !req.getBirthDate().isBlank()) {
                p.setBirthDate(LocalDate.parse(req.getBirthDate()));
            }
            p.setEmail(req.getEmail());
            p = patientRepository.save(p);
            profileId = p.getPatientId();
            profileCode = p.getPatientCode();
        } else {
            Doctor d = new Doctor();
            d.setUserId(user.getUserId());
            d.setFhirResourceId("Practitioner-" + UUID.randomUUID());
            d.setDoctorCode(nextDoctorCode());
            String[] names = splitName(req.getFullName());
            d.setFirstName(names[0]);
            d.setLastName(names[1]);
            d.setSpecialization(req.getSpecialization());
            d.setEmail(req.getEmail());
            d = doctorRepository.save(d);
            profileId = d.getDoctorId();
            profileCode = d.getDoctorCode();
        }

        auditService.log(user.getUserId(), role.name(), "REGISTER", "User", user.getUserId().toString(),
                "New " + role + " account created: " + user.getUsername());

        return ResponseEntity.status(HttpStatus.CREATED).body(
                new AuthResponse(user.getUserId(), user.getUsername(), user.getFullName(), role.name(), profileId, profileCode));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        var userOpt = userRepository.findByUsername(req.getUsername());
        if (userOpt.isEmpty() || !passwordEncoder.matches(req.getPassword(), userOpt.get().getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid username or password"));
        }
        User user = userOpt.get();
        if (user.getStatus() != User.Status.ACTIVE) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Account is " + user.getStatus() + ". Contact the administrator."));
        }
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        Long profileId = null;
        String profileCode = null;
        if (user.getRole() == User.Role.PATIENT) {
            var p = patientRepository.findByUserId(user.getUserId());
            if (p.isPresent()) { profileId = p.get().getPatientId(); profileCode = p.get().getPatientCode(); }
        } else if (user.getRole() == User.Role.DOCTOR) {
            var d = doctorRepository.findByUserId(user.getUserId());
            if (d.isPresent()) { profileId = d.get().getDoctorId(); profileCode = d.get().getDoctorCode(); }
        }

        auditService.log(user.getUserId(), user.getRole().name(), "LOGIN", "User", user.getUserId().toString(),
                user.getUsername() + " logged in");

        return ResponseEntity.ok(new AuthResponse(user.getUserId(), user.getUsername(), user.getFullName(),
                user.getRole().name(), profileId, profileCode));
    }


    private String nextPatientCode() {
        long n = 1000 + patientRepository.count() + 1;
        while (patientRepository.findByPatientCode("PAT-" + n).isPresent()) n++;
        return "PAT-" + n;
    }

    private String nextDoctorCode() {
        long n = 2000 + doctorRepository.count() + 1;
        while (doctorRepository.findByDoctorCode("DOC-" + n).isPresent()) n++;
        return "DOC-" + n;
    }

    private String[] splitName(String fullName) {
        if (fullName == null || fullName.isBlank()) return new String[]{"New", "User"};
        String[] parts = fullName.trim().split("\\s+", 2);
        return parts.length == 2 ? parts : new String[]{parts[0], "-"};
    }
}