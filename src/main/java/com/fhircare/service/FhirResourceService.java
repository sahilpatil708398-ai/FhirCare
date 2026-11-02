package com.fhircare.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fhircare.entity.FhirResource;
import com.fhircare.entity.MedicalRecord;
import com.fhircare.entity.Patient;
import com.fhircare.repository.FhirResourceRepository;
import com.fhircare.repository.MedicalRecordRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a lightweight, FHIR R5-shaped JSON Bundle for a patient
 * (Patient resource + their Condition / Observation / MedicationRequest /
 * Procedure / DiagnosticReport / Immunization / AllergyIntolerance entries)
 * and persists it into the D5 "FHIR Resources" table, exactly as shown in
 * process 5.0 "Generate FHIR Resources" of the project DFD.
 */
@Service
public class FhirResourceService {

    private final FhirResourceRepository fhirResourceRepository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FhirResourceService(FhirResourceRepository fhirResourceRepository,
                                MedicalRecordRepository medicalRecordRepository) {
        this.fhirResourceRepository = fhirResourceRepository;
        this.medicalRecordRepository = medicalRecordRepository;
    }

    public FhirResource generateForPatient(Patient patient) {
        List<MedicalRecord> records = medicalRecordRepository.findByPatientIdOrderByRecordDateDesc(patient.getPatientId());

        Map<String, Object> patientResource = new LinkedHashMap<>();
        patientResource.put("resourceType", "Patient");
        patientResource.put("id", patient.getFhirResourceId());
        patientResource.put("identifier", List.of(Map.of("system", "urn:fhircare:patient-code", "value", patient.getPatientCode())));
        patientResource.put("active", patient.getActive());
        patientResource.put("name", List.of(Map.of("family", patient.getLastName(), "given", List.of(patient.getFirstName()))));
        patientResource.put("gender", patient.getGender());
        patientResource.put("birthDate", patient.getBirthDate() != null ? patient.getBirthDate().toString() : null);
        patientResource.put("telecom", List.of(
                Map.of("system", "phone", "value", patient.getPhone() == null ? "" : patient.getPhone()),
                Map.of("system", "email", "value", patient.getEmail() == null ? "" : patient.getEmail())
        ));
        patientResource.put("address", List.of(Map.of("city", patient.getCity(), "state", patient.getState())));

        List<Map<String, Object>> entries = new ArrayList<>();
        entries.add(Map.of("resource", patientResource));
        for (MedicalRecord r : records) {
            entries.add(Map.of("resource", buildRecordResource(r)));
        }

        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("resourceType", "Bundle");
        bundle.put("type", "collection");
        bundle.put("timestamp", LocalDateTime.now().toString());
        bundle.put("total", entries.size());
        bundle.put("entry", entries);

        String json;
        try {
            json = objectMapper.writeValueAsString(bundle);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize FHIR bundle", e);
        }

        FhirResource resource = fhirResourceRepository
                .findByResourceTypeAndResourceId("Bundle", patient.getFhirResourceId())
                .orElseGet(FhirResource::new);
        resource.setResourceType("Bundle");
        resource.setResourceId(patient.getFhirResourceId());
        resource.setPatientId(patient.getPatientId());
        resource.setResourceJson(json);
        resource.setVersionNumber(resource.getVersionNumber() == null ? 1 : resource.getVersionNumber() + 1);
        resource.setUpdatedAt(LocalDateTime.now());
        return fhirResourceRepository.save(resource);
    }

    private Map<String, Object> buildRecordResource(MedicalRecord r) {
        String fhirType = switch (r.getRecordType()) {
            case CONDITION -> "Condition";
            case OBSERVATION -> "Observation";
            case MEDICATION -> "MedicationRequest";
            case PROCEDURE -> "Procedure";
            case REPORT -> "DiagnosticReport";
            case IMMUNIZATION -> "Immunization";
            case ALLERGY -> "AllergyIntolerance";
        };
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("resourceType", fhirType);
        res.put("id", r.getFhirResourceId());
        res.put("status", r.getStatus());
        res.put("code", Map.of("text", r.getTitle()));
        res.put("subject", Map.of("reference", "Patient/" + r.getPatientId()));
        if (r.getRecordDate() != null) {
            res.put("effectiveDate", r.getRecordDate().toString());
        }
        if (r.getValueText() != null) {
            res.put("valueString", r.getValueText() + (r.getUnit() != null ? " " + r.getUnit() : ""));
        }
        if (r.getDescription() != null) {
            res.put("note", List.of(Map.of("text", r.getDescription())));
        }
        return res;
    }
}
