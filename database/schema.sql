-- ============================================================================
-- FHIRCare - Secure Patient Medical Record Exchange System
-- MySQL schema (import via phpMyAdmin / MySQL Workbench, XAMPP MySQL running)
-- Tables map 1:1 to the data stores in the project DFD:
--   D1 Patient Data          -> patients
--   D2 Medical Records       -> medical_records
--   D3 Access Requests &
--      Consents              -> access_requests, consents
--   D4 Users & Roles         -> users
--   D5 FHIR Resources (JSON) -> fhir_resources
--   D6 Audit Logs            -> audit_logs
-- ============================================================================

CREATE DATABASE IF NOT EXISTS fhircare_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE fhircare_db;

SET FOREIGN_KEY_CHECKS = 0;

-- ---------------------------------------------------------------------------
-- D4: Users & Roles
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
  user_id        BIGINT AUTO_INCREMENT PRIMARY KEY,
  username       VARCHAR(100) UNIQUE NOT NULL,
  email          VARCHAR(150) UNIQUE NOT NULL,
  password_hash  VARCHAR(255) NOT NULL,
  full_name      VARCHAR(200) NOT NULL,
  role           ENUM('PATIENT','DOCTOR','ADMIN') NOT NULL,
  status         ENUM('ACTIVE','INACTIVE','LOCKED') DEFAULT 'ACTIVE',
  created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  last_login     TIMESTAMP NULL
);

-- ---------------------------------------------------------------------------
-- D1: Patient Data
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS patients (
  patient_id       BIGINT AUTO_INCREMENT PRIMARY KEY,
  fhir_resource_id VARCHAR(100) UNIQUE NOT NULL,
  patient_code     VARCHAR(50)  UNIQUE NOT NULL,
  user_id          BIGINT NULL,
  first_name       VARCHAR(100) NOT NULL,
  last_name        VARCHAR(100) NOT NULL,
  gender           VARCHAR(20),
  birth_date       DATE,
  blood_group      VARCHAR(10),
  phone            VARCHAR(30),
  email            VARCHAR(150),
  address          TEXT,
  city             VARCHAR(100),
  state            VARCHAR(100),
  active           BOOLEAN DEFAULT TRUE,
  created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  profile_submitted BOOLEAN DEFAULT FALSE,
  profile_submitted_at TIMESTAMP NULL,
  profile_edit_unlocked BOOLEAN DEFAULT FALSE,
  profile_edit_unlocked_by_doctor_id BIGINT NULL,
  profile_edit_unlocked_at TIMESTAMP NULL,
  FOREIGN KEY (user_id) REFERENCES users(user_id)
);

-- Doctors (FHIR Practitioner)
CREATE TABLE IF NOT EXISTS doctors (
  doctor_id        BIGINT AUTO_INCREMENT PRIMARY KEY,
  fhir_resource_id VARCHAR(100) UNIQUE NOT NULL,
  doctor_code      VARCHAR(50) UNIQUE NOT NULL,
  user_id          BIGINT NULL,
  first_name       VARCHAR(100) NOT NULL,
  last_name        VARCHAR(100) NOT NULL,
  specialization   VARCHAR(150),
  qualification    VARCHAR(150),
  license_number   VARCHAR(100),
  phone            VARCHAR(30),
  email            VARCHAR(150),
  active           BOOLEAN DEFAULT TRUE,
  created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(user_id)
);

-- ---------------------------------------------------------------------------
-- D2: Medical Records
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS medical_records (
  record_id        BIGINT AUTO_INCREMENT PRIMARY KEY,
  fhir_resource_id VARCHAR(100) UNIQUE NOT NULL,
  patient_id       BIGINT NOT NULL,
  doctor_id        BIGINT NULL,
  record_type      ENUM('CONDITION','OBSERVATION','MEDICATION','PROCEDURE','REPORT','IMMUNIZATION','ALLERGY') NOT NULL,
  title            VARCHAR(255) NOT NULL,
  description      TEXT,
  value_text       VARCHAR(255),
  unit             VARCHAR(50),
  status           VARCHAR(50) DEFAULT 'ACTIVE',
  record_date      DATE,
  created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (patient_id) REFERENCES patients(patient_id) ON DELETE CASCADE,
  FOREIGN KEY (doctor_id) REFERENCES doctors(doctor_id)
);

-- ---------------------------------------------------------------------------
-- D3: Access Requests & Consents
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS access_requests (
  request_id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  patient_id             BIGINT NOT NULL,
  requesting_doctor_id   BIGINT NOT NULL,
  reason                 VARCHAR(500),
  status                 ENUM('PENDING','APPROVED','REJECTED','REVOKED') DEFAULT 'PENDING',
  verification_code      VARCHAR(10) NULL,
  code_verified           BOOLEAN DEFAULT FALSE,
  code_verified_at        TIMESTAMP NULL,
  requested_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  decided_at             TIMESTAMP NULL,
  FOREIGN KEY (patient_id) REFERENCES patients(patient_id) ON DELETE CASCADE,
  FOREIGN KEY (requesting_doctor_id) REFERENCES doctors(doctor_id)
);

-- If you already have an existing database from before this feature, run:
--   ALTER TABLE access_requests
--     ADD COLUMN verification_code VARCHAR(10) NULL,
--     ADD COLUMN code_verified BOOLEAN DEFAULT FALSE,
--     ADD COLUMN code_verified_at TIMESTAMP NULL;
-- (spring.jpa.hibernate.ddl-auto=update will also add these columns automatically on next app start)

CREATE TABLE IF NOT EXISTS consents (
  consent_id       BIGINT AUTO_INCREMENT PRIMARY KEY,
  access_request_id BIGINT NULL,
  patient_id       BIGINT NOT NULL,
  doctor_id        BIGINT NOT NULL,
  purpose          VARCHAR(255) DEFAULT 'Treatment / Second opinion',
  status           ENUM('GRANTED','DENIED','REVOKED') NOT NULL,
  granted_at       TIMESTAMP NULL,
  revoked_at       TIMESTAMP NULL,
  FOREIGN KEY (access_request_id) REFERENCES access_requests(request_id),
  FOREIGN KEY (patient_id) REFERENCES patients(patient_id) ON DELETE CASCADE,
  FOREIGN KEY (doctor_id) REFERENCES doctors(doctor_id)
);

-- ---------------------------------------------------------------------------
-- D5: FHIR Resources (canonical JSON, FHIR R5 shaped)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS fhir_resources (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  resource_type  VARCHAR(100) NOT NULL,
  resource_id    VARCHAR(100) NOT NULL,
  patient_id     BIGINT NULL,
  version_number INT DEFAULT 1,
  resource_json  JSON NOT NULL,
  created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_fhir_resource (resource_type, resource_id),
  FOREIGN KEY (patient_id) REFERENCES patients(patient_id) ON DELETE CASCADE
);

-- ---------------------------------------------------------------------------
-- D6: Audit Logs
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS audit_logs (
  audit_id      BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id       BIGINT NULL,
  user_role     VARCHAR(30),
  action        VARCHAR(100) NOT NULL,
  resource_type VARCHAR(100),
  resource_id   VARCHAR(100),
  description   VARCHAR(500),
  event_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(user_id)
);

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================================
-- Seed / demo data
-- Demo login passwords (plain text shown here only for the README / tester):
--   admin    / admin123
--   dranaya  / doctor123   (Dr. Ananya Sharma  - referring doctor, has access)
--   drvikram / doctor123   (Dr. Vikram Kulkarni - receiving doctor, requests access)
--   rahul    / patient123  (Patient PAT-1001)
-- Hashes below are real BCrypt hashes of the passwords above.
-- ============================================================================

INSERT INTO users (username, email, password_hash, full_name, role, status) VALUES
 ('admin',    'admin@fhircare.local',    '$2b$10$7H3NBvvf0gjpp5MRpuQxnemXjDFoFOZcMY9SdOZUUFsWcLr1qEM3W', 'System Administrator', 'ADMIN',  'ACTIVE'),
 ('dranaya',  'ananya.sharma@citycare.example',  '$2b$10$c7NwFds72dvfd1Lz1KT2Y.NYTF9ziEVVtg8vgkPRSyG6HUYOdeSUi', 'Dr. Ananya Sharma',   'DOCTOR', 'ACTIVE'),
 ('drvikram', 'vikram.kulkarni@hopediag.example', '$2b$10$c7NwFds72dvfd1Lz1KT2Y.NYTF9ziEVVtg8vgkPRSyG6HUYOdeSUi', 'Dr. Vikram Kulkarni', 'DOCTOR', 'ACTIVE'),
 ('rahul',    'rahul@example.com', '$2b$10$oD3Dt9qCL5g/569ZCzWq6uwADOVLlijkdw37AlnJGRI8KvSaJenLG', 'Rahul Patil', 'PATIENT', 'ACTIVE')
ON DUPLICATE KEY UPDATE username = username;

INSERT INTO doctors (fhir_resource_id, doctor_code, user_id, first_name, last_name, specialization, qualification, license_number, phone, email) VALUES
 ('Practitioner-doc-001', 'DOC-2001', (SELECT user_id FROM users WHERE username='dranaya'),  'Ananya', 'Sharma',   'General Physician', 'MBBS, MD', 'MH-LIC-4521', '9822011223', 'ananya.sharma@citycare.example'),
 ('Practitioner-doc-002', 'DOC-2002', (SELECT user_id FROM users WHERE username='drvikram'), 'Vikram', 'Kulkarni', 'Endocrinologist',   'MBBS, DM', 'MH-LIC-7788', '9822033445', 'vikram.kulkarni@hopediag.example')
ON DUPLICATE KEY UPDATE doctor_code = doctor_code;

INSERT INTO patients (fhir_resource_id, patient_code, user_id, first_name, last_name, gender, birth_date, blood_group, phone, email, city, state, active) VALUES
 ('Patient-demo-001', 'PAT-1001', (SELECT user_id FROM users WHERE username='rahul'), 'Rahul', 'Patil', 'male', '1984-06-18', 'O+', '9876543210', 'rahul@example.com', 'Pune', 'Maharashtra', TRUE)
ON DUPLICATE KEY UPDATE patient_code = patient_code;

INSERT INTO medical_records (fhir_resource_id, patient_id, doctor_id, record_type, title, description, value_text, unit, status, record_date) VALUES
 ('Observation-bp-001',  (SELECT patient_id FROM patients WHERE patient_code='PAT-1001'), (SELECT doctor_id FROM doctors WHERE doctor_code='DOC-2001'), 'OBSERVATION', 'Blood pressure', 'Routine checkup reading', '120/80', 'mmHg', 'FINAL', '2026-06-18'),
 ('Condition-t2d-001',   (SELECT patient_id FROM patients WHERE patient_code='PAT-1001'), (SELECT doctor_id FROM doctors WHERE doctor_code='DOC-2001'), 'CONDITION',   'Type 2 diabetes', 'Diagnosed and being managed with Metformin', NULL, NULL, 'ACTIVE', '2023-02-10'),
 ('MedicationRequest-met-001', (SELECT patient_id FROM patients WHERE patient_code='PAT-1001'), (SELECT doctor_id FROM doctors WHERE doctor_code='DOC-2001'), 'MEDICATION', 'Metformin 500 mg', 'Twice daily after meals', '500', 'mg', 'ONGOING', '2026-06-16'),
 ('DiagnosticReport-cbc-001', (SELECT patient_id FROM patients WHERE patient_code='PAT-1001'), (SELECT doctor_id FROM doctors WHERE doctor_code='DOC-2001'), 'REPORT', 'Complete blood count', 'All parameters within normal range', NULL, NULL, 'FINAL', '2026-06-14')
ON DUPLICATE KEY UPDATE title = title;

-- Dr. Ananya already has consent (referring / primary doctor)
INSERT INTO consents (access_request_id, patient_id, doctor_id, purpose, status, granted_at) VALUES
 (NULL, (SELECT patient_id FROM patients WHERE patient_code='PAT-1001'), (SELECT doctor_id FROM doctors WHERE doctor_code='DOC-2001'), 'Primary care', 'GRANTED', NOW())
ON DUPLICATE KEY UPDATE status = status;

-- Dr. Vikram has a pending request to view Rahul's records (matches Level-2 DFD flow)
INSERT INTO access_requests (patient_id, requesting_doctor_id, reason, status) VALUES
 ((SELECT patient_id FROM patients WHERE patient_code='PAT-1001'), (SELECT doctor_id FROM doctors WHERE doctor_code='DOC-2002'), 'Endocrinology second opinion on diabetes management', 'PENDING')
ON DUPLICATE KEY UPDATE reason = reason;
