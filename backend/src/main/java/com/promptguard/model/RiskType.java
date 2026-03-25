package com.promptguard.model;

public enum RiskType {
    SECRET,       // Passwords, API keys, tokens
    PII,          // Email, phone, Aadhaar, SSN
    PHI,          // Protected Health Information (HIPAA) — MRN, ICD codes, diagnoses
    SOURCE_CODE,  // Java, Python, SQL code
    KEYWORD,      // Blocked keywords (global)
    ORG_KEYWORD,  // Org-specific keyword from user_keyword_policies table
    NONE          // No risk
}
