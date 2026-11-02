package com.fhircare.controller;

import com.fhircare.entity.Consent;
import com.fhircare.entity.Doctor;
import com.fhircare.entity.MedicalRecord;
import com.fhircare.entity.Patient;
import com.fhircare.repository.ConsentRepository;
import com.fhircare.repository.DoctorRepository;
import com.fhircare.repository.MedicalRecordRepository;
import com.fhircare.repository.PatientRepository;
import com.fhircare.service.AuditService;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Generates a downloadable "hard copy" PDF of a patient's full medical
 * record (all conditions/diseases, observations, medications, reports…)
 * so it can be printed or shared for emergencies / referrals.
 *
 * Access rules mirror MedicalRecordController: the patient can always
 * download their own report; a doctor may only download it if they hold
 * a GRANTED consent for that patient (i.e. their access request was
 * approved by the patient AND the verification code was confirmed).
 */
@RestController
@RequestMapping("/api/reports")
@CrossOrigin
public class ReportController {

    private final PatientRepository patientRepo;
    private final MedicalRecordRepository recordRepo;
    private final ConsentRepository consentRepo;
    private final DoctorRepository doctorRepo;
    private final AuditService auditService;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    public ReportController(PatientRepository patientRepo, MedicalRecordRepository recordRepo,
                             ConsentRepository consentRepo, DoctorRepository doctorRepo,
                             AuditService auditService) {
        this.patientRepo = patientRepo;
        this.recordRepo = recordRepo;
        this.consentRepo = consentRepo;
        this.doctorRepo = doctorRepo;
        this.auditService = auditService;
    }

    @GetMapping("/patient/{patientId}/pdf")
    public ResponseEntity<?> downloadPdf(@PathVariable Long patientId,
                                          @RequestParam(required = false) Long doctorId) {
        Patient patient = patientRepo.findById(patientId).orElse(null);
        if (patient == null) return ResponseEntity.notFound().build();

        if (doctorId != null) {
            boolean granted = consentRepo.existsByPatientIdAndDoctorIdAndStatus(patientId, doctorId, Consent.Status.GRANTED);
            if (!granted) {
                return ResponseEntity.status(403).body(Map.of("error",
                        "No approved (code-verified) consent for this patient yet."));
            }
        }

        List<MedicalRecord> records = recordRepo.findByPatientIdOrderByRecordDateDesc(patientId);

        try {
            byte[] pdf = buildPdf(patient, records);

            auditService.log(null, doctorId != null ? "DOCTOR" : "PATIENT", "DOWNLOAD_REPORT",
                    "Patient", patient.getPatientCode(),
                    (doctorId != null ? "Doctor #" + doctorId : "Patient") + " downloaded PDF report for " + patient.getPatientCode());

            String filename = "FHIRCare-Report-" + patient.getPatientCode() + ".pdf";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdf);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Could not generate PDF: " + e.getMessage()));
        }
    }

    private byte[] buildPdf(Patient p, List<MedicalRecord> records) throws Exception {
        Document doc = new Document(PageSize.A4, 40, 40, 50, 40);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter.getInstance(doc, out);
        doc.open();

        Font titleFont = new Font(Font.HELVETICA, 18, Font.BOLD, new Color(0x30, 0x27, 0x8f));
        Font subFont = new Font(Font.HELVETICA, 9, Font.NORMAL, Color.GRAY);
        Font h2Font = new Font(Font.HELVETICA, 12, Font.BOLD, new Color(0x1e, 0x1b, 0x3a));
        Font labelFont = new Font(Font.HELVETICA, 8, Font.BOLD, Color.GRAY);
        Font valueFont = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.BLACK);
        Font cellHeadFont = new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE);
        Font cellFont = new Font(Font.HELVETICA, 9, Font.NORMAL, Color.BLACK);

        Paragraph title = new Paragraph("FHIRCare — Patient Medical Report", titleFont);
        doc.add(title);
        doc.add(new Paragraph("HL7 FHIR R5 concepts · generated " +
                java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")), subFont));
        doc.add(Chunk.NEWLINE);

        // ---- patient profile block ----
        doc.add(new Paragraph("Patient profile", h2Font));
        PdfPTable profile = new PdfPTable(4);
        profile.setWidthPercentage(100);
        profile.setSpacingBefore(6);
        addKV(profile, "Name", p.getFirstName() + " " + p.getLastName(), labelFont, valueFont);
        addKV(profile, "Patient code", p.getPatientCode(), labelFont, valueFont);
        addKV(profile, "Date of birth", p.getBirthDate() != null ? p.getBirthDate().format(DATE_FMT) : "—", labelFont, valueFont);
        addKV(profile, "Gender", nz(p.getGender()), labelFont, valueFont);
        addKV(profile, "Blood group", nz(p.getBloodGroup()), labelFont, valueFont);
        addKV(profile, "Phone", nz(p.getPhone()), labelFont, valueFont);
        addKV(profile, "City / State", nz(p.getCity()) + " / " + nz(p.getState()), labelFont, valueFont);
        addKV(profile, "FHIR resource id", p.getFhirResourceId(), labelFont, valueFont);
        doc.add(profile);
        doc.add(Chunk.NEWLINE);

        // ---- conditions / diseases summary (the "what does this patient have" view) ----
        List<MedicalRecord> conditions = records.stream()
                .filter(r -> r.getRecordType() == MedicalRecord.RecordType.CONDITION)
                .toList();
        List<MedicalRecord> allergies = records.stream()
                .filter(r -> r.getRecordType() == MedicalRecord.RecordType.ALLERGY)
                .toList();

        doc.add(new Paragraph("Diagnosed conditions (" + conditions.size() + ")", h2Font));
        doc.add(recordsTable(conditions, cellHeadFont, cellFont));
        doc.add(Chunk.NEWLINE);

        if (!allergies.isEmpty()) {
            doc.add(new Paragraph("Allergies (" + allergies.size() + ")", h2Font));
            doc.add(recordsTable(allergies, cellHeadFont, cellFont));
            doc.add(Chunk.NEWLINE);
        }

        // ---- everything else (observations, medications, procedures, reports, immunizations) ----
        List<MedicalRecord> rest = records.stream()
                .filter(r -> r.getRecordType() != MedicalRecord.RecordType.CONDITION
                        && r.getRecordType() != MedicalRecord.RecordType.ALLERGY)
                .toList();
        doc.add(new Paragraph("Other records (" + rest.size() + ")", h2Font));
        doc.add(recordsTable(rest, cellHeadFont, cellFont));

        doc.add(Chunk.NEWLINE);
        doc.add(new Paragraph("This report is generated from FHIRCare and is intended for the named patient's " +
                "treating doctors in emergency or routine care situations. It is not a substitute for direct clinical judgement.", subFont));

        doc.close();
        return out.toByteArray();
    }

    private PdfPTable recordsTable(List<MedicalRecord> list, Font headFont, Font cellFont) {
        PdfPTable table = new PdfPTable(new float[]{2.2f, 1.3f, 1.8f, 3.2f, 1.3f});
        table.setWidthPercentage(100);
        table.setSpacingBefore(4);
        for (String h : new String[]{"Title", "Type", "Date", "Notes / value", "Status"}) {
            PdfPCell c = new PdfPCell(new Phrase(h, headFont));
            c.setBackgroundColor(new Color(0x30, 0x27, 0x8f));
            c.setPadding(5);
            table.addCell(c);
        }
        if (list.isEmpty()) {
            PdfPCell empty = new PdfPCell(new Phrase("None on file.", cellFont));
            empty.setColspan(5);
            empty.setPadding(6);
            table.addCell(empty);
            return table;
        }
        for (MedicalRecord r : list) {
            table.addCell(cell(r.getTitle(), cellFont));
            table.addCell(cell(r.getRecordType().name(), cellFont));
            table.addCell(cell(r.getRecordDate() != null ? r.getRecordDate().format(DATE_FMT) : "—", cellFont));
            String notes = (r.getDescription() != null ? r.getDescription() : "") +
                    (r.getValueText() != null ? (r.getDescription() != null ? " · " : "") + r.getValueText() + (r.getUnit() != null ? " " + r.getUnit() : "") : "");
            table.addCell(cell(notes.isBlank() ? "—" : notes, cellFont));
            table.addCell(cell(nz(r.getStatus()), cellFont));
        }
        return table;
    }

    private PdfPCell cell(String text, Font f) {
        PdfPCell c = new PdfPCell(new Phrase(text == null ? "—" : text, f));
        c.setPadding(5);
        return c;
    }

    private void addKV(PdfPTable table, String label, String value, Font labelFont, Font valueFont) {
        PdfPCell l = new PdfPCell();
        l.setBorder(Rectangle.NO_BORDER);
        l.addElement(new Phrase(label.toUpperCase(), labelFont));
        l.addElement(new Phrase(value == null || value.isBlank() ? "—" : value, valueFont));
        l.setPadding(6);
        table.addCell(l);
    }

    private String nz(String s) {
        return (s == null || s.isBlank()) ? "—" : s;
    }
}
