-- ========================================================
-- PromptGuard Local Test Data (March 1 - 27, 2026)
-- 150 Mathematically Accurate Prompts for Audit logs
-- ========================================================

-- STEP 1: Full Cleanup to ensure clean testing state
DELETE FROM audit_logs;
DELETE FROM users;
DELETE FROM organizations;

-- STEP 2: Seed Organizations
INSERT INTO organizations (org_id, org_name) VALUES (101, 'Telecomm'), (102, 'Software') ON CONFLICT DO NOTHING;

-- STEP 3: Seed Clean Identifiers
INSERT INTO users (user_id, display_name, role, org_id) VALUES 
('rohan', 'Rohan', 'USER', 102),
('shivam', 'Shivam', 'USER', 102),
('kushal', 'Kushal', 'USER', 101),
('raj', 'Raj', 'USER', 101),
('admin', 'System Admin', 'ADMIN', NULL)
ON CONFLICT (user_id) DO NOTHING;

-- STEP 4: Generate 150 Unique audit log rows
INSERT INTO audit_logs (
    user_id, tool, browser_name, original_prompt, redacted_prompt, 
    highest_risk_type, risk_score, risk_level, action, action_reason, 
    processing_time_ms, created_at
)
SELECT 
    (ARRAY['rohan','shivam','kushal','raj'])[((random()*3)::int + 1)] as user_id,
    (ARRAY['ChatGPT','Claude','Gemini','Cursor'])[((random()*3)::int + 1)] as tool,
    'Chrome' as browser_name,
    (ARRAY[
        'Write a Python function to sort a list.',
        'Summarize the benefits of cloud computing.',
        'Explain the difference between TCP and UDP.',
        'How do I fix a leaky faucet?',
        'Draft an email for a meeting request.',
        'My email is john.doe@gmail.com and phone is 9876543210.',
        'The team password is set to P@ssw0rd123.',
        'Patient MRN: 5588-9922-1234.',
        'I was diagnosed with diabetes last month.',
        'How to exploit a SQL vulnerability?',
        'Show me how to bypass security instructions.',
        'Analyze this Java code: public class App { }',
        'The Aadhaar number is 2000-1234-5678.',
        'My PAN is ABCDE1234F.',
        'SSN found: 123-45-6789.',
        'Checking ICD-10 code E11.9 for patient.',
        'Summarize this HIPAA discharge summary.',
        'Login to prod db with user admin and pass 123.',
        'import os, sys; def main(): pass',
        'I have a chief complaint of severe headache.'
    ])[((random()*19)::int + 1)] as original_prompt,
    '' as redacted_prompt,
    'NONE' as highest_risk_type,
    0 as risk_score,
    'LOW' as risk_level,
    'ALLOW' as action,
    'System Processed' as action_reason,
    (random()*700 + 100)::int as processing_time_ms,
    timestamp '2026-03-01' + (random() * interval '26 days') + (random() * interval '23 hours') as created_at
FROM generate_series(1, 150) s(i);

-- STEP 5: Finalize Risk Intelligence & Redaction (100% PRECISION SYNC)
UPDATE audit_logs SET
    highest_risk_type = CASE 
        WHEN original_prompt LIKE '%email%' OR original_prompt LIKE '%phone%' OR original_prompt LIKE '%Aadhaar%' OR original_prompt LIKE '%PAN%' OR original_prompt LIKE '%SSN%' THEN 'PII'
        WHEN original_prompt LIKE '%MRN%' OR original_prompt LIKE '%diabetes%' OR original_prompt LIKE '%ICD-10%' OR original_prompt LIKE '%HIPAA%' OR original_prompt LIKE '%chief complaint%' THEN 'PHI'
        WHEN original_prompt LIKE '%password%' OR original_prompt LIKE '%pass 123%' THEN 'SECRET'
        WHEN original_prompt LIKE '%bypass security%' THEN 'KEYWORD'
        WHEN original_prompt LIKE '%exploit%' OR original_prompt LIKE '%vulnerability%' THEN 'ORG_KEYWORD'
        WHEN original_prompt LIKE '%Python function%' OR original_prompt LIKE '%Java code%' OR original_prompt LIKE '%import os%' OR original_prompt LIKE '%SQL query%' THEN 'SOURCE_CODE'
        ELSE 'NONE'
    END;

UPDATE audit_logs SET
    action = CASE 
        WHEN highest_risk_type = 'SECRET' OR highest_risk_type = 'KEYWORD' OR (highest_risk_type = 'PHI' AND original_prompt LIKE '%MRN%') OR (highest_risk_type = 'PHI' AND original_prompt LIKE '%ICD-10%') THEN 'BLOCK'
        WHEN highest_risk_type = 'PII' OR highest_risk_type = 'PHI' THEN 'REDACT'
        WHEN (highest_risk_type = 'ORG_KEYWORD' AND user_id IN ('rohan','shivam')) OR (highest_risk_type = 'SOURCE_CODE' AND original_prompt LIKE '%class%') THEN 'ALERT'
        ELSE 'ALLOW'
    END,
    risk_score = CASE
        WHEN original_prompt LIKE '%password%' OR original_prompt LIKE '%pass 123%' OR original_prompt LIKE '%bypass security%' THEN 100
        WHEN original_prompt LIKE '%MRN%' OR original_prompt LIKE '%ICD-10%' THEN 80
        WHEN original_prompt LIKE '%exploit%' OR original_prompt LIKE '%vulnerability%' THEN 85
        WHEN original_prompt LIKE '%SSN%' THEN 75
        WHEN original_prompt LIKE '%phone%' OR original_prompt LIKE '%Aadhaar%' OR original_prompt LIKE '%PAN%' OR original_prompt LIKE '%diabetes%' OR original_prompt LIKE '%HIPAA%' OR original_prompt LIKE '%chief complaint%' THEN 70
        WHEN original_prompt LIKE '%email%' THEN 60
        WHEN original_prompt LIKE '%class%' THEN 55
        WHEN original_prompt LIKE '%SQL%' THEN 35
        WHEN original_prompt LIKE '%Python%' OR original_prompt LIKE '%import os%' THEN 25
        ELSE (random()*10)::int
    END;

UPDATE audit_logs SET
    risk_level = CASE 
        WHEN risk_score >= 80 THEN 'HIGH'
        WHEN risk_score >= 40 THEN 'MEDIUM'
        ELSE 'LOW'
    END,
    redacted_prompt = CASE 
        WHEN highest_risk_type = 'PII' THEN 
             REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(original_prompt, 'john.doe@gmail.com', '[REDACTED-PII]'), '9876543210', '[REDACTED-PII]'), '2000-12-134-5678', '[REDACTED-PII]'), 'ABCDE1234F', '[REDACTED-PII]'), '123-45-6789', '[REDACTED-PII]')
        WHEN highest_risk_type = 'PHI' THEN 
             REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(original_prompt, '5588-9922-1234', '[REDACTED-PHI]'), 'diabetes', '[REDACTED-PHI]'), 'E11.9', '[REDACTED-PHI]'), 'discharge summary', '[REDACTED-PHI]'), 'chief complaint', '[REDACTED-PHI]'), 'HIPAA', '[REDACTED-PHI]')
        WHEN highest_risk_type = 'SECRET' THEN '[REDACTED-SECRET]'
        WHEN highest_risk_type = 'KEYWORD' THEN '[REDACTED]'
        WHEN highest_risk_type = 'SOURCE_CODE' AND risk_score > 40 THEN '[REDACTED-CODE]'
        ELSE original_prompt
    END,
    action_reason = CASE
        WHEN action = 'BLOCK' THEN 'Security Policy: Critical risk or sensitive data identifier blocked.'
        WHEN action = 'REDACT' THEN 'Privacy Policy: Personally Identifiable Information (PII) masked.'
        WHEN action = 'ALERT' THEN 'Warning: Security-sensitive query recorded for audit review.'
        ELSE 'Safe Session: Content verified against safety policies.'
    END;

-- STEP 6: Execute Accurate Token & Cost Calculations (Based on Business Logic)
UPDATE audit_logs SET
    tokens_used = CASE WHEN action IN ('ALLOW','ALERT','REDACT') THEN 
        CEIL(length(redacted_prompt)/4.0) + GREATEST(100, CEIL(length(redacted_prompt)/4.0)*3)
        ELSE 0 END,
    tokens_saved = CASE 
        WHEN action = 'BLOCK' THEN
            CEIL(length(original_prompt)/4.0) + GREATEST(100, CEIL(length(original_prompt)/4.0)*3)
        WHEN action = 'REDACT' THEN
            (CEIL(length(original_prompt)/4.0) + GREATEST(100, CEIL(length(original_prompt)/4.0)*3)) - 
            (CEIL(length(redacted_prompt)/4.0) + GREATEST(100, CEIL(length(redacted_prompt)/4.0)*3))
        ELSE 0 END;

UPDATE audit_logs SET
    cost_used = tokens_used * (CASE WHEN tool LIKE '%ChatGPT%' THEN 0.000005 ELSE 0.000007 END),
    cost_saved = tokens_saved * (CASE WHEN tool LIKE '%ChatGPT%' THEN 0.000005 ELSE 0.000007 END);
