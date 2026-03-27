-- ========================================================
-- PromptGuard Local Test Data (March 1 - 27, 2026)
-- 150 Sample Prompts for rohan, kushal, shivam, raj
-- ========================================================

-- STEP 1: Full Cleanup to ensure clean testing state
DELETE FROM audit_logs;
DELETE FROM users;
DELETE FROM organizations;

-- STEP 2: Insert Pure Data
INSERT INTO organizations (org_id, org_name) VALUES (101, 'Telecomm') ON CONFLICT (org_id) DO NOTHING;
INSERT INTO organizations (org_id, org_name) VALUES (102, 'Software') ON CONFLICT (org_id) DO NOTHING;

INSERT INTO users (user_id, display_name, role) VALUES ('rohan', 'Rohan', 'USER') ON CONFLICT (user_id) DO NOTHING;
INSERT INTO users (user_id, display_name, role) VALUES ('shivam', 'Shivam', 'USER') ON CONFLICT (user_id) DO NOTHING;
INSERT INTO users (user_id, display_name, role) VALUES ('kushal', 'Kushal', 'USER') ON CONFLICT (user_id) DO NOTHING;
INSERT INTO users (user_id, display_name, role) VALUES ('raj', 'Raj', 'USER') ON CONFLICT (user_id) DO NOTHING;
INSERT INTO users (user_id, display_name, role) VALUES ('admin', 'System Admin', 'ADMIN') ON CONFLICT (user_id) DO NOTHING;

-- STEP 3: Generate Volume (150 rows)
INSERT INTO audit_logs (user_id, tool, browser_name, original_prompt, redacted_prompt, highest_risk_type, risk_score, risk_level, action, created_at)
SELECT 
    CASE (random()*4)::int % 4
        WHEN 0 THEN 'rohan'
        WHEN 1 THEN 'shivam'
        WHEN 2 THEN 'kushal'
        ELSE 'raj'
    END as user_id,
    CASE (random()*4)::int % 4
        WHEN 0 THEN 'ChatGPT'
        WHEN 1 THEN 'Claude'
        WHEN 2 THEN 'Gemini'
        ELSE 'Cursor'
    END as tool,
    'Chrome' as browser_name,
    'Sample Prompt ' || i as original_prompt,
    'Sample Prompt ' || i as redacted_prompt,
    CASE (random()*4)::int % 4
        WHEN 0 THEN 'NONE'
        WHEN 1 THEN 'PII'
        WHEN 2 THEN 'FINANCIAL'
        ELSE 'MALICIOUS'
    END as highest_risk_type,
    (random()*100)::int as risk_score,
    'MEDIUM' as risk_level,
    CASE (random()*4)::int % 4
        WHEN 0 THEN 'ALLOW'
        WHEN 1 THEN 'REDACT'
        WHEN 2 THEN 'BLOCK'
        ELSE 'ALERT'
    END as action,
    timestamp '2026-03-01' + (random() * interval '26 days') as created_at
FROM generate_series(1, 150) s(i);

-- STEP 4: Refine attributes for realism
UPDATE audit_logs SET risk_level = 'HIGH' WHERE risk_score > 75;
UPDATE audit_logs SET risk_level = 'LOW' WHERE risk_score < 30;
UPDATE audit_logs SET action = 'ALLOW', highest_risk_type = 'NONE' WHERE action = 'ALLOW';
UPDATE audit_logs SET redacted_prompt = REPLACE(original_prompt, 'Sample', '[REDACTED]') WHERE action = 'REDACT';
