package com.promptguard.detector;

import com.promptguard.model.DetectionResult;
import com.promptguard.model.RiskType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PhiDetector — detects Protected Health Information (PHI)
 * based on HIPAA Safe Harbor method (45 CFR §164.514(b)).
 *
 * HIPAA defines 18 PHI identifiers. We cover the most detectable ones:
 *
 *   MRN pattern         score=80 → BLOCK  (Medical Record Number)
 *   ICD-10 code         score=80 → BLOCK  (Diagnosis code e.g. E11.9)
 *   NPI number          score=80 → BLOCK  (Provider identifier)
 *   DOB in prompt       score=75 → REDACT (Date of birth patterns)
 *   Medication keywords score=70 → REDACT (Drug names + dosage)
 *   Diagnosis keywords  score=70 → REDACT (Clinical terms)
 *   Insurance/Policy    score=65 → REDACT (Health plan beneficiary)
 *
 * Why separate from PiiDetector?
 *   HIPAA violations carry different penalties (up to $1.9M per category).
 *   PHI needs its own audit trail, redaction placeholder, and policy path.
 *
 * Score design:
 *   MRN/ICD/NPI → score=80 → RiskLevel=CRITICAL → PolicyEngine BLOCK
 *   Others       → score<80 → RiskLevel=HIGH    → PolicyEngine REDACT
 *   All use RiskType.PHI so PolicyEngine routes to REDACT branch,
 *   but MRN/ICD/NPI score≥80 hits the BLOCK fallthrough first.
 */
@Component
public class PhiDetector {

    // ── Structural identifiers (regex-based) ─────────────────────────────

    // Medical Record Number: MRN followed by digits (common hospital format)
    private static final Pattern MRN = Pattern.compile(
        "\\b(MRN|mrn|medical record|patient id|patient#)[:\\s#-]*\\d{4,12}\\b",
        Pattern.CASE_INSENSITIVE);

    // ICD-10 diagnosis codes: Letter + 2 digits + optional decimal + more digits
    // e.g. E11.9 (Type 2 diabetes), J18.9 (Pneumonia), C50.911 (Breast cancer)
    private static final Pattern ICD10 = Pattern.compile(
        "\\b[A-TV-Z][0-9]{2}(\\.?[A-Z0-9]{1,4})?\\b");

    // NPI (National Provider Identifier): 10-digit number preceded by NPI label
    private static final Pattern NPI = Pattern.compile(
        "\\b(NPI|npi)[:\\s#-]*\\d{10}\\b",
        Pattern.CASE_INSENSITIVE);

    // Date of birth patterns: DOB / date of birth + various date formats
    private static final Pattern DOB = Pattern.compile(
        "\\b(dob|date of birth|born on|birth date)[:\\s]*"
        + "(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}|\\d{4}[/-]\\d{1,2}[/-]\\d{1,2})",
        Pattern.CASE_INSENSITIVE);

    // Health insurance / policy number
    private static final Pattern INSURANCE = Pattern.compile(
        "\\b(insurance policy|health plan|member id|policy number|group number)[:\\s#-]*[A-Z0-9]{6,20}\\b",
        Pattern.CASE_INSENSITIVE);

    // ── Keyword-based identifiers ─────────────────────────────────────────

    // Common medication names + dosage pattern (e.g. "metformin 500mg")
    // This list covers the 50 most commonly mentioned drug classes
    private static final Set<String> MEDICATION_KEYWORDS = Set.of(
        "metformin", "lisinopril", "atorvastatin", "omeprazole", "amlodipine",
        "metoprolol", "albuterol", "losartan", "gabapentin", "hydrochlorothiazide",
        "sertraline", "simvastatin", "montelukast", "pantoprazole", "escitalopram",
        "levothyroxine", "amoxicillin", "azithromycin", "ciprofloxacin", "prednisone",
        "tramadol", "cyclobenzaprine", "clonazepam", "alprazolam", "lorazepam",
        "oxycodone", "hydrocodone", "morphine", "fentanyl", "insulin",
        "warfarin", "clopidogrel", "aspirin therapy", "chemotherapy", "dialysis"
    );

    // Clinical diagnosis terms that indicate health condition disclosure
    private static final Set<String> DIAGNOSIS_KEYWORDS = Set.of(
        "diagnosed with", "patient has", "patient presents", "chief complaint",
        "medical history", "clinical notes", "discharge summary", "lab results",
        "test results", "biopsy", "pathology report", "radiology report",
        "patient is positive for", "hiv positive", "cancer diagnosis",
        "psychiatric evaluation", "mental health record", "therapy notes",
        "substance abuse", "addiction treatment", "rehabilitation for",
        "surgical history", "post-operative", "pre-operative",
        "patient allergic", "drug allergy", "blood type",
        "vital signs", "blood pressure reading", "glucose level",
        "hemoglobin a1c", "cholesterol level", "ecg result", "ekg result"
    );

    // ── detect entry point ────────────────────────────────────────────────

    public List<DetectionResult> detect(String prompt) {
        List<DetectionResult> results = new ArrayList<>();
        if (prompt == null || prompt.isBlank()) return results;

        // Structural checks (regex)
        checkPattern(prompt, MRN,       "MRN (Medical Record Number)", 80, results);
        checkPattern(prompt, ICD10,     "ICD-10 diagnosis code",        80, results);
        checkPattern(prompt, NPI,       "NPI (Provider Identifier)",    80, results);
        checkPattern(prompt, DOB,       "Date of birth",                75, results);
        checkPattern(prompt, INSURANCE, "Health insurance/policy ID",   65, results);

        // Keyword checks
        checkKeywords(prompt, MEDICATION_KEYWORDS, "Medication name", 70, results);
        checkKeywords(prompt, DIAGNOSIS_KEYWORDS,  "Clinical/diagnosis term", 70, results);

        return results;
    }

    private void checkPattern(String prompt, Pattern p, String label, int score,
                               List<DetectionResult> results) {
        Matcher m = p.matcher(prompt);
        if (m.find()) {
            results.add(new DetectionResult(
                RiskType.PHI,
                score,
                "PHI detected: " + label,
                m.group()
            ));
        }
    }

    private void checkKeywords(String prompt, Set<String> keywords, String label,
                                int score, List<DetectionResult> results) {
        String lower = prompt.toLowerCase();
        for (String kw : keywords) {
            if (lower.contains(kw.toLowerCase())) {
                results.add(new DetectionResult(
                    RiskType.PHI,
                    score,
                    "PHI detected: " + label + " — \"" + kw + "\"",
                    kw
                ));
                return; // one hit per category is enough
            }
        }
    }
}
