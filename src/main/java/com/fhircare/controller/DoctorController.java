package com.fhircare.controller;

import com.fhircare.entity.Doctor;
import com.fhircare.repository.DoctorRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/doctors")
@CrossOrigin
public class DoctorController {

    private final DoctorRepository repo;

    public DoctorController(DoctorRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public List<Doctor> all() {
        return repo.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Doctor> get(@PathVariable Long id) {
        return repo.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/code/{code}")
    public ResponseEntity<Doctor> byCode(@PathVariable String code) {
        return repo.findByDoctorCode(code).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/by-user/{userId}")
    public ResponseEntity<Doctor> byUser(@PathVariable Long userId) {
        return repo.findByUserId(userId).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }
}
