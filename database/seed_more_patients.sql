-- ============================================================================
-- FHIRCare - extra demo data: more patients, each with MULTIPLE diseases
-- (this is the "one patient -> many diseases -> many doctors" case from the
-- ER sketch / problem statement: a patient can have several medical
-- histories from several doctors, and it should be easy to see all of them
-- together in one place, e.g. for an emergency).
--
-- Run this AFTER database/schema.sql has created the tables once.
-- Safe to re-run (ON DUPLICATE KEY UPDATE guards).
--
-- Disease/record names and resource groupings follow the HL7 FHIR R5
-- Clinical Module index: https://www.hl7.org/fhir/clinicalsummary-module.html
--   Condition          -> https://www.hl7.org/fhir/condition.html
--   AllergyIntolerance -> https://www.hl7.org/fhir/allergyintolerance.html
--   Observation        -> https://www.hl7.org/fhir/observation.html
--   MedicationRequest  -> https://www.hl7.org/fhir/medicationrequest.html
-- ============================================================================

USE fhircare_db;

-- ---------------------------------------------------------------------------
-- One more doctor (so "My Patients" / access requests have a 3rd practitioner)
-- ---------------------------------------------------------------------------
INSERT INTO users (username, email, password_hash, full_name, role, status) VALUES
 ('drnayan', 'nayan.deshmukh@carewell.example', '$2b$10$c7NwFds72dvfd1Lz1KT2Y.NYTF9ziEVVtg8vgkPRSyG6HUYOdeSUi', 'Dr. Nayan Deshmukh', 'DOCTOR', 'ACTIVE')
ON DUPLICATE KEY UPDATE username = username;

INSERT INTO doctors (fhir_resource_id, doctor_code, user_id, first_name, last_name, specialization, qualification, license_number, phone, email) VALUES
 ('Practitioner-doc-003', 'DOC-2003', (SELECT user_id FROM users WHERE username='drnayan'), 'Nayan', 'Deshmukh', 'Cardiologist', 'MBBS, DM Cardiology', 'MH-LIC-9012', '9822099001', 'nayan.deshmukh@carewell.example')
ON DUPLICATE KEY UPDATE doctor_code = doctor_code;

-- ---------------------------------------------------------------------------
-- More patients (login password for all: patient123)
-- ---------------------------------------------------------------------------
INSERT INTO users (username, email, password_hash, full_name, role, status) VALUES
 ('rohit',  'rohit.sharma@example.com',  '$2b$10$oD3Dt9qCL5g/569ZCzWq6uwADOVLlijkdw37AlnJGRI8KvSaJenLG', 'Rohit Sharma',  'PATIENT', 'ACTIVE'),
 ('sneha',  'sneha.patil@example.com',   '$2b$10$oD3Dt9qCL5g/569ZCzWq6uwADOVLlijkdw37AlnJGRI8KvSaJenLG', 'Sneha Patil',   'PATIENT', 'ACTIVE'),
 ('amit',   'amit.kulkarni@example.com', '$2b$10$oD3Dt9qCL5g/569ZCzWq6uwADOVLlijkdw37AlnJGRI8KvSaJenLG', 'Amit Kulkarni', 'PATIENT', 'ACTIVE'),
 ('neha',   'neha.joshi@example.com',    '$2b$10$oD3Dt9qCL5g/569ZCzWq6uwADOVLlijkdw37AlnJGRI8KvSaJenLG', 'Neha Joshi',    'PATIENT', 'ACTIVE'),
 ('sandeep','sandeep.mane@example.com',  '$2b$10$oD3Dt9qCL5g/569ZCzWq6uwADOVLlijkdw37AlnJGRI8KvSaJenLG', 'Sandeep Mane',  'PATIENT', 'ACTIVE'),
 ('priya',  'priya.shinde@example.com',  '$2b$10$oD3Dt9qCL5g/569ZCzWq6uwADOVLlijkdw37AlnJGRI8KvSaJenLG', 'Priya Shinde',  'PATIENT', 'ACTIVE')
ON DUPLICATE KEY UPDATE username = username;

INSERT INTO patients (fhir_resource_id, patient_code, user_id, first_name, last_name, gender, birth_date, blood_group, phone, email, city, state, active) VALUES
 ('Patient-demo-002', 'PAT-1002', (SELECT user_id FROM users WHERE username='rohit'),   'Rohit',   'Sharma',  'male',   '1990-03-11', 'B+',  '9876500002', 'rohit.sharma@example.com',  'Kolhapur', 'Maharashtra', TRUE),
 ('Patient-demo-003', 'PAT-1003', (SELECT user_id FROM users WHERE username='sneha'),   'Sneha',   'Patil',   'female', '1996-07-22', 'A+',  '9876500003', 'sneha.patil@example.com',   'Pune',     'Maharashtra', TRUE),
 ('Patient-demo-007', 'PAT-1007', (SELECT user_id FROM users WHERE username='amit'),    'Amit',    'Kulkarni','male',   '1978-11-02', 'O-',  '9876500007', 'amit.kulkarni@example.com', 'Kolhapur', 'Maharashtra', TRUE),
 ('Patient-demo-010', 'PAT-1010', (SELECT user_id FROM users WHERE username='neha'),    'Neha',    'Joshi',   'female', '2001-01-30', 'AB+', '9876500010', 'neha.joshi@example.com',    'Satara',   'Maharashtra', TRUE),
 ('Patient-demo-005', 'PAT-1005', (SELECT user_id FROM users WHERE username='sandeep'), 'Sandeep', 'Mane',    'male',   '1965-05-14', 'B-',  '9876500005', 'sandeep.mane@example.com',  'Sangli',   'Maharashtra', TRUE),
 ('Patient-demo-006', 'PAT-1006', (SELECT user_id FROM users WHERE username='priya'),   'Priya',   'Shinde',  'female', '1988-09-09', 'A-',  '9876500006', 'priya.shinde@example.com',  'Pune',     'Maharashtra', TRUE)
ON DUPLICATE KEY UPDATE patient_code = patient_code;

-- ---------------------------------------------------------------------------
-- Multiple diseases per patient, several with more than one doctor involved
-- (this is exactly the "hard to detect root cause across doctors" problem
-- from the notes — all of it now sits under one patient_id).
-- ---------------------------------------------------------------------------

-- Rohit Sharma (PAT-1002) — cardiometabolic cluster, seen by 2 doctors
INSERT INTO medical_records (fhir_resource_id, patient_id, doctor_id, record_type, title, description, value_text, unit, status, record_date) VALUES
 ('Condition-htn-1002',  (SELECT patient_id FROM patients WHERE patient_code='PAT-1002'), (SELECT doctor_id FROM doctors WHERE doctor_code='DOC-2001'), 'CONDITION', 'Essential hypertension', 'Stage 1, on lifestyle modification + medication', NULL, NULL, 'ACTIVE', '2024-01-12'),
 ('Condition-t2d-1002',  (SELECT patient_id FROM patients WHERE patient_code='PAT-1002'), (SELECT doctor_id FROM doctors WHERE doctor_code='DOC-2002'), 'CONDITION', 'Type 2 diabetes mellitus', 'HbA1c 7.8% at diagnosis', NULL, NULL, 'ACTIVE', '2024-02-20'),
 ('Condition-cad-1002',  (SELECT patient_id FROM patients WHERE patient_code='PAT-1002'), (SELECT doctor_id FROM doctors WHERE doctor_code='DOC-2003'), 'CONDITION', 'Coronary artery disease', 'Single-vessel, stent placed LAD', NULL, NULL, 'ACTIVE', '2025-06-02'),
 ('AllergyIntolerance-1002-a', (SELECT patient_id FROM patients WHERE patient_code='PAT-1002'), (SELECT doctor_id FROM doctors WHERE doctor_code='DOC-2001'), 'ALLERGY', 'Penicillin allergy', 'Rash on exposure', NULL, NULL, 'ACTIVE', '2019-04-01'),
 ('MedicationRequest-1002-a', (SELECT patient_id FROM patients WHERE patient_code='PAT-1002'), (SELECT doctor_id FROM doctors WHERE doctor_code='DOC-2003'), 'MEDICATION', 'Atorvastatin 20 mg', 'Once daily at night', '20', 'mg', 'ONGOING', '2025-06-02'),
 ('Observation-1002-a', (SELECT patient_id FROM patients WHERE patient_code='PAT-1002'), (SELECT doctor_id FROM doctors WHERE doctor_code='DOC-2001'), 'OBSERVATION', 'Blood pressure', 'Routine follow-up', '138/88', 'mmHg', 'FINAL', '2026-05-10')
ON DUPLICATE KEY UPDATE title = title;

-- Sneha Patil (PAT-1003) — respiratory + allergy cluster
INSERT INTO medical_records (fhir_resource_id, patient_id, doctor_id, record_type, title, description, value_text, unit, status, record_date) VALUES
 ('Condition-asthma-1003', (SELECT patient_id FROM patients WHERE patient_code='PAT-1003'), (SELECT doctor_id FROM doctors WHERE doctor_code='DOC-2001'), 'CONDITION', 'Bronchial asthma', 'Moderate persistent, triggered by dust and cold air', NULL, NULL, 'ACTIVE', '2021-09-05'),
 ('Condition-rhinitis-1003', (SELECT patient_id FROM patients WHERE patient_code='PAT-1003'), (SELECT doctor_id FROM doctors WHERE doctor_code='DOC-2001'), 'CONDITION', 'Seasonal allergic rhinitis', 'Worse Nov-Feb', NULL, NULL, 'ACTIVE', '2021-09-05'),
 ('AllergyIntolerance-1003-a', (SELECT patient_id FROM patients WHERE patient_code='PAT-1003'), (SELECT doctor_id FROM doctors WHERE doctor_code='DOC-2001'), 'ALLERGY', 'Dust mite allergy', 'Confirmed on skin prick test', NULL, NULL, 'ACTIVE', '2021-09-05'),
 ('MedicationRequest-1003-a', (SELECT patient_id FROM patients WHERE patient_code='PAT-1003'), (SELECT doctor_id FROM doctors WHERE doctor_code='DOC-2001'), 'MEDICATION', 'Budesonide-Formoterol inhaler', 'Two puffs twice daily', NULL, NULL, 'ONGOING', '2026-01-15'),
 ('Observation-1003-a', (SELECT patient_id FROM patients WHERE patient_code='PAT-1003'), (SELECT doctor_id FROM doctors WHERE doctor_code='DOC-2001'), 'OBSERVATION', 'SpO2 at rest', 'Routine check', '97', '%', 'FINAL', '2026-06-01')
ON DUPLICATE KEY UPDATE title = title;

-- Amit Kulkarni (PAT-1007) — older patient, multiple chronic conditions across 3 doctors
INSERT INTO medical_records (fhir_resource_id, patient_id, doctor_id, record_type, title, description, value_text, unit, status, record_date) VALUES
 ('Condition-ckd-1007', (SELECT patient_id FROM patients WHERE patient_code='PAT-1007'), (SELECT doctor_id FROM doctors WHERE doctor_code='DOC-2002'), 'CONDITION', 'Chronic kidney disease, stage 3', 'eGFR 45, monitored quarterly', NULL, NULL, 'ACTIVE', '2022-03-18'),
 ('Condition-htn-1007',  (SELECT patient_id FROM patients WHERE patient_code='PAT-1007'), (SELECT doctor_id FROM doctors WHERE doctor_code='DOC-2001'), 'CONDITION', 'Essential hypertension', 'Long-standing, on 2 agents', NULL, NULL, 'ACTIVE', '2015-01-01'),
 ('Condition-oa-1007',   (SELECT patient_id FROM patients WHERE patient_code='PAT-1007'), (SELECT doctor_id FROM doctors WHERE doctor_code='DOC-2003'), 'CONDITION', 'Osteoarthritis, both knees', 'Grade 2, managed conservatively', NULL, NULL, 'ACTIVE', '2023-08-09'),
 ('MedicationRequest-1007-a', (SELECT patient_id FROM patients WHERE patient_code='PAT-1007'), (SELECT doctor_id FROM doctors WHERE doctor_code='DOC-2002'), 'MEDICATION', 'Losartan 50 mg', 'Once daily morning', '50', 'mg', 'ONGOING', '2026-03-01'),
 ('DiagnosticReport-1007-a', (SELECT patient_id FROM patients WHERE patient_code='PAT-1007'), (SELECT doctor_id FROM doctors WHERE doctor_code='DOC-2002'), 'REPORT', 'Renal function panel', 'Creatinine 1.6 mg/dL, stable vs last visit', NULL, NULL, 'FINAL', '2026-07-11')
ON DUPLICATE KEY UPDATE title = title;

-- Neha Joshi (PAT-1010) — young patient, mental health + GI
INSERT INTO medical_records (fhir_resource_id, patient_id, doctor_id, record_type, title, description, value_text, unit, status, record_date) VALUES
 ('Condition-gerd-1010', (SELECT patient_id FROM patients WHERE patient_code='PAT-1010'), (SELECT doctor_id FROM doctors WHERE doctor_code='DOC-2001'), 'CONDITION', 'Gastro-oesophageal reflux disease', 'Symptomatic, diet + PPI', NULL, NULL, 'ACTIVE', '2025-02-14'),
 ('Condition-migraine-1010', (SELECT patient_id FROM patients WHERE patient_code='PAT-1010'), (SELECT doctor_id FROM doctors WHERE doctor_code='DOC-2003'), 'CONDITION', 'Migraine without aura', 'Episodic, 2-3 per month', NULL, NULL, 'ACTIVE', '2024-11-20'),
 ('MedicationRequest-1010-a', (SELECT patient_id FROM patients WHERE patient_code='PAT-1010'), (SELECT doctor_id FROM doctors WHERE doctor_code='DOC-2001'), 'MEDICATION', 'Pantoprazole 40 mg', 'Once daily before breakfast', '40', 'mg', 'ONGOING', '2026-02-14')
ON DUPLICATE KEY UPDATE title = title;

-- Sandeep Mane (PAT-1005) — older patient, endocrine + eye
INSERT INTO medical_records (fhir_resource_id, patient_id, doctor_id, record_type, title, description, value_text, unit, status, record_date) VALUES
 ('Condition-t2d-1005', (SELECT patient_id FROM patients WHERE patient_code='PAT-1005'), (SELECT doctor_id FROM doctors WHERE doctor_code='DOC-2002'), 'CONDITION', 'Type 2 diabetes mellitus', 'Diagnosed 12 years ago, insulin-dependent', NULL, NULL, 'ACTIVE', '2014-05-01'),
 ('Condition-hypothy-1005', (SELECT patient_id FROM patients WHERE patient_code='PAT-1005'), (SELECT doctor_id FROM doctors WHERE doctor_code='DOC-2002'), 'CONDITION', 'Hypothyroidism', 'On levothyroxine replacement', NULL, NULL, 'ACTIVE', '2016-02-11'),
 ('Condition-cataract-1005', (SELECT patient_id FROM patients WHERE patient_code='PAT-1005'), (SELECT doctor_id FROM doctors WHERE doctor_code='DOC-2003'), 'CONDITION', 'Cataract, right eye', 'Surgery planned next quarter', NULL, NULL, 'ACTIVE', '2026-04-01'),
 ('MedicationRequest-1005-a', (SELECT patient_id FROM patients WHERE patient_code='PAT-1005'), (SELECT doctor_id FROM doctors WHERE doctor_code='DOC-2002'), 'MEDICATION', 'Levothyroxine 75 mcg', 'Once daily, empty stomach', '75', 'mcg', 'ONGOING', '2026-02-11')
ON DUPLICATE KEY UPDATE title = title;

-- Priya Shinde (PAT-1006) — autoimmune + skin
INSERT INTO medical_records (fhir_resource_id, patient_id, doctor_id, record_type, title, description, value_text, unit, status, record_date) VALUES
 ('Condition-ra-1006', (SELECT patient_id FROM patients WHERE patient_code='PAT-1006'), (SELECT doctor_id FROM doctors WHERE doctor_code='DOC-2001'), 'CONDITION', 'Rheumatoid arthritis', 'Seropositive, on DMARDs', NULL, NULL, 'ACTIVE', '2020-10-03'),
 ('Condition-psoriasis-1006', (SELECT patient_id FROM patients WHERE patient_code='PAT-1006'), (SELECT doctor_id FROM doctors WHERE doctor_code='DOC-2001'), 'CONDITION', 'Plaque psoriasis', 'Mild, topical treatment', NULL, NULL, 'ACTIVE', '2021-06-19'),
 ('AllergyIntolerance-1006-a', (SELECT patient_id FROM patients WHERE patient_code='PAT-1006'), (SELECT doctor_id FROM doctors WHERE doctor_code='DOC-2001'), 'ALLERGY', 'Sulfa drug allergy', 'Hives on exposure', NULL, NULL, 'ACTIVE', '2018-01-01')
ON DUPLICATE KEY UPDATE title = title;

-- A couple more diseases for the original demo patient Rahul (PAT-1001) too,
-- so even the very first demo login shows "multiple diseases, multiple doctors".
INSERT INTO medical_records (fhir_resource_id, patient_id, doctor_id, record_type, title, description, value_text, unit, status, record_date) VALUES
 ('Condition-htn-1001',   (SELECT patient_id FROM patients WHERE patient_code='PAT-1001'), (SELECT doctor_id FROM doctors WHERE doctor_code='DOC-2001'), 'CONDITION', 'Essential hypertension', 'Mild, diet-controlled', NULL, NULL, 'ACTIVE', '2024-04-10'),
 ('AllergyIntolerance-1001-a', (SELECT patient_id FROM patients WHERE patient_code='PAT-1001'), (SELECT doctor_id FROM doctors WHERE doctor_code='DOC-2001'), 'ALLERGY', 'Peanut allergy', 'Mild oral reaction', NULL, NULL, 'ACTIVE', '2010-01-01'),
 ('Condition-gerd-1001',  (SELECT patient_id FROM patients WHERE patient_code='PAT-1001'), (SELECT doctor_id FROM doctors WHERE doctor_code='DOC-2003'), 'CONDITION', 'Gastro-oesophageal reflux disease', 'Intermittent, diet related', NULL, NULL, 'ACTIVE', '2025-09-02')
ON DUPLICATE KEY UPDATE title = title;

-- A couple more pending / example access requests so "Access Requests" tiles have data
INSERT INTO access_requests (patient_id, requesting_doctor_id, reason, status) VALUES
 ((SELECT patient_id FROM patients WHERE patient_code='PAT-1002'), (SELECT doctor_id FROM doctors WHERE doctor_code='DOC-2003'), 'Cardiology review after stent placement', 'PENDING'),
 ((SELECT patient_id FROM patients WHERE patient_code='PAT-1007'), (SELECT doctor_id FROM doctors WHERE doctor_code='DOC-2003'), 'Joint pain assessment', 'PENDING')
ON DUPLICATE KEY UPDATE reason = reason;
