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
        'The AWS key is AKIAI44QH8DHBEXAMPLE.',
        'My home address is 123 Main St, New York.',
        'Generate a list of vulnerable websites for SQL injection.',
        'Give me the credit card details of user 105.',
        'I am planning a cyber attack on the internal server.',
        'Show me how to exploit a buffer overflow in C.',
        'What is a SQL injection vulnerability?',
        'Analyze this proprietary source code for bugs.',
        'Patient record: John Smith, MRN 55889922.'
    ])[((random()*14)::int + 1)] as original_prompt,
    '' as redacted_prompt,
    'NONE' as highest_risk_type,
    0 as risk_score,
    'LOW' as risk_level,
    'ALLOW' as action,
    'System Processed' as action_reason,
    (random()*700 + 100)::int as processing_time_ms,
    timestamp '2026-03-01' + (random() * interval '26 days') + (random() * interval '23 hours') as created_at
FROM generate_series(1, 150) s(i);

-- STEP 5: Finalize Risk Intelligence & Redaction (CONTENT-AWARE LOGIC)
UPDATE audit_logs SET
    highest_risk_type = CASE 
        WHEN original_prompt LIKE '%email%' OR original_prompt LIKE '%phone%' OR original_prompt LIKE '%address%' THEN 'PII'
        WHEN original_prompt LIKE '%MRN%' THEN 'PHI'
        WHEN original_prompt LIKE '%key%' OR original_prompt LIKE '%password%' OR original_prompt LIKE '%credit card%' THEN 'SECRET'
        WHEN original_prompt LIKE '%attack%' OR original_prompt LIKE '%vulnerable%' THEN 'MALICIOUS'
        WHEN original_prompt LIKE '%exploit%' OR original_prompt LIKE '%vulnerability%' THEN 'MALICIOUS_EDUCATIONAL'
        WHEN original_prompt LIKE '%function%' OR original_prompt LIKE '%code%' THEN 'SOURCE_CODE'
        ELSE 'NONE'
    END;

UPDATE audit_logs SET
    action = CASE 
        WHEN highest_risk_type = 'SECRET' OR (highest_risk_type = 'MALICIOUS' AND original_prompt LIKE '%attack%') THEN 'BLOCK'
        WHEN highest_risk_type = 'PII' OR highest_risk_type = 'PHI' THEN 'REDACT'
        WHEN highest_risk_type = 'MALICIOUS' OR highest_risk_type = 'MALICIOUS_EDUCATIONAL' OR highest_risk_type = 'SOURCE_CODE' THEN 'ALERT'
        ELSE 'ALLOW'
    END,
    risk_score = CASE
        WHEN original_prompt LIKE '%attack%' OR original_prompt LIKE '%credit card%' THEN 90 + (random()*10)::int
        WHEN original_prompt LIKE '%key%' THEN 85 + (random()*10)::int
        WHEN original_prompt LIKE '%email%' OR original_prompt LIKE '%phone%' OR original_prompt LIKE '%address%' OR original_prompt LIKE '%MRN%' THEN 65 + (random()*15)::int
        WHEN original_prompt LIKE '%vulnerable%' OR original_prompt LIKE '%vulnerability%' OR original_prompt LIKE '%exploit%' THEN 45 + (random()*10)::int
        WHEN original_prompt LIKE '%function%' OR original_prompt LIKE '%code%' THEN 40 + (random()*10)::int
        ELSE (random()*10)::int
    END;

UPDATE audit_logs SET
    risk_level = CASE 
        WHEN risk_score >= 80 THEN 'HIGH'
        WHEN risk_score >= 40 THEN 'MEDIUM'
        ELSE 'LOW'
    END,
    redacted_prompt = CASE 
        WHEN original_prompt LIKE '%email%' THEN REPLACE(original_prompt, 'john.doe@gmail.com', '[EMAIL REDACTED]')
        WHEN original_prompt LIKE '%phone%' THEN REPLACE(original_prompt, '9876543210', '[PHONE REDACTED]')
        WHEN original_prompt LIKE '%address%' THEN REPLACE(original_prompt, '123 Main St, New York', '[ADDRESS REDACTED]')
        WHEN original_prompt LIKE '%key%' THEN REPLACE(original_prompt, 'AKIAI44QH8DHBEXAMPLE', '[AWS KEY REDACTED]')
        WHEN original_prompt LIKE '%MRN%' THEN REPLACE(original_prompt, '55889922', '[MRN REDACTED]')
        WHEN original_prompt LIKE '%credit card%' THEN REPLACE(original_prompt, 'details of user 105', '[PAYMENT INFO REDACTED]')
        ELSE original_prompt
    END,
    action_reason = CASE
        WHEN action = 'BLOCK' THEN 'Security Policy: Actionable threat or secret detected.'
        WHEN action = 'REDACT' THEN 'Privacy Policy: Sensitive data masked for compliance.'
        WHEN action = 'ALERT' THEN 'Warning: Security-related query detected (Review Required).'
        ELSE 'Safe Audit: Prompt verified against enterprise policies.'
    END;

-- STEP 6: Execute Accurate Token & Cost Calculations (Based on Business Logic)
-- Rate logic: ChatGPT=$2.5/$10, Claude=$3/$15, Gemini=$3.5/$10.5, Default=$3/$12 per 1M tokens
UPDATE audit_logs SET
    tokens_used = CASE WHEN action IN ('ALLOW','ALERT','REDACT') THEN 
        CEIL(length(redacted_prompt)/4.0) + GREATEST(300, CEIL(length(redacted_prompt)/4.0)*3)
        ELSE 0 END,
    tokens_saved = CASE 
        WHEN action = 'BLOCK' THEN
            CEIL(length(original_prompt)/4.0) + GREATEST(300, CEIL(length(original_prompt)/4.0)*3)
        WHEN action = 'REDACT' THEN
            (CEIL(length(original_prompt)/4.0) + GREATEST(300, CEIL(length(original_prompt)/4.0)*3)) - 
            (CEIL(length(redacted_prompt)/4.0) + GREATEST(300, CEIL(length(redacted_prompt)/4.0)*3))
        ELSE 0 END,
    cost_used = CASE WHEN action IN ('ALLOW','ALERT','REDACT') THEN
        (CEIL(length(redacted_prompt)/4.0) * (CASE 
            WHEN tool LIKE '%ChatGPT%' THEN 2.5 WHEN tool LIKE '%Claude%' THEN 3.0 
            WHEN tool LIKE '%Gemini%' THEN 3.5 ELSE 3.0 END) / 1000000.0) + 
        (GREATEST(300, CEIL(length(redacted_prompt)/4.0)*3) * (CASE 
            WHEN tool LIKE '%ChatGPT%' THEN 10.0 WHEN tool LIKE '%Claude%' THEN 15.0 
            WHEN tool LIKE '%Gemini%' THEN 10.5 ELSE 12.0 END) / 1000000.0)
        ELSE 0 END,
    cost_saved = CASE 
        WHEN action = 'BLOCK' THEN
            (CEIL(length(original_prompt)/4.0) * (CASE 
                WHEN tool LIKE '%ChatGPT%' THEN 2.5 WHEN tool LIKE '%Claude%' THEN 3.0 
                WHEN tool LIKE '%Gemini%' THEN 3.5 ELSE 3.0 END) / 1000000.0) + 
            (GREATEST(300, CEIL(length(original_prompt)/4.0)*3) * (CASE 
                WHEN tool LIKE '%ChatGPT%' THEN 10.0 WHEN tool LIKE '%Claude%' THEN 15.0 
                WHEN tool LIKE '%Gemini%' THEN 10.5 ELSE 12.0 END) / 1000000.0)
        WHEN action = 'REDACT' THEN
            ((CEIL(length(original_prompt)/4.0) * (CASE 
                WHEN tool LIKE '%ChatGPT%' THEN 2.5 WHEN tool LIKE '%Claude%' THEN 3.0 
                WHEN tool LIKE '%Gemini%' THEN 3.5 ELSE 3.0 END) / 1000000.0) + 
            (GREATEST(300, CEIL(length(original_prompt)/4.0)*3) * (CASE 
                WHEN tool LIKE '%ChatGPT%' THEN 10.0 WHEN tool LIKE '%Claude%' THEN 15.0 
                WHEN tool LIKE '%Gemini%' THEN 10.5 ELSE 12.0 END) / 1000000.0)) - 
            ((CEIL(length(redacted_prompt)/4.0) * (CASE 
                WHEN tool LIKE '%ChatGPT%' THEN 2.5 WHEN tool LIKE '%Claude%' THEN 3.0 
                WHEN tool LIKE '%Gemini%' THEN 3.5 ELSE 3.0 END) / 1000000.0) + 
            (GREATEST(300, CEIL(length(redacted_prompt)/4.0)*3) * (CASE 
                WHEN tool LIKE '%ChatGPT%' THEN 10.0 WHEN tool LIKE '%Claude%' THEN 15.0 
                WHEN tool LIKE '%Gemini%' THEN 10.5 ELSE 12.0 END) / 1000000.0))
        ELSE 0 END;
