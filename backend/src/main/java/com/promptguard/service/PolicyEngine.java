package com.promptguard.service;

import com.promptguard.model.*;
import org.springframework.stereotype.Service;

@Service
public class PolicyEngine {

    /**
     * Decision priority (ORDER MATTERS):
     *
     * 1. SECRET      → BLOCK  (credentials must never reach AI)
     * 2. KEYWORD     → BLOCK  (global admin-defined forbidden words)
     * 3. ORG_KEYWORD → 4 possible outcomes based on score:
     *                    score=100 → BLOCK    (block_col)    absolute block
     *                    score=85  → ALERT    (critial_col)  critical severity alert ← FIXED
     *                    score=75  → REDACT   (redacted_col) remove keyword, send rest
     *                    score=0   → ALLOW    (allow_col)    never reaches here (skipped in detector)
     * 4. PHI         → BLOCK if score≥80 (MRN/ICD/NPI), else REDACT
     * 5. PII         → REDACT (SSN, CC, Aadhaar, PAN, phone, email)
     * 6. SOURCE_CODE → ALERT
     * 7. score≥80    → BLOCK
     * 8. score≥60    → REDACT
     * 9. score≥40    → ALERT
     * 10. else       → ALLOW
     */
    public PolicyDecision decide(RiskScore riskScore) {
        int      score = riskScore.getTotalScore();
        RiskType type  = riskScore.getRiskType();

        // 1. SECRET → always BLOCK
        if (type == RiskType.SECRET) {
            return new PolicyDecision(
                Action.BLOCK,
                "Secret/credential detected (score: " + score + "/100). "
                    + "Sending credentials to AI tools is a critical security risk.");
        }

        // 2. Global KEYWORD → always BLOCK
        if (type == RiskType.KEYWORD) {
            return new PolicyDecision(
                Action.BLOCK,
                "Blocked keyword detected. This content cannot be sent to AI tools.");
        }

        // 3. ORG_KEYWORD — 4 outcomes driven by score set in UserKeywordDetector
        if (type == RiskType.ORG_KEYWORD) {
            if (score == 100) {
                // block_col = true → absolute block
                return new PolicyDecision(
                    Action.BLOCK,
                    "Organisation policy: keyword is on the BLOCK list. "
                        + "Prompt cannot be sent to AI tools. (score: " + score + "/100)");
            }
            if (score >= 80) {
                // criticalCol = true → score=85 → ALERT (not block — user can see and decide)
                return new PolicyDecision(
                    Action.ALERT,
                    "Organisation policy: CRITICAL keyword detected. "
                        + "Prompt flagged as critical severity. (score: " + score + "/100)");
            }
            if (score >= 60) {
                // redacted_col = true → score=75 → redact and send
                return new PolicyDecision(
                    Action.REDACT,
                    "Organisation policy: sensitive keyword redacted before sending. "
                        + "(score: " + score + "/100)");
            }
            // allow_col case never reaches here — detector skips it entirely
        }

        // 4. PHI → BLOCK if structural identifiers (MRN/ICD/NPI), else REDACT
        if (type == RiskType.PHI) {
            if (score >= 80) {
                return new PolicyDecision(
                    Action.BLOCK,
                    "Protected Health Information (PHI) detected — structural identifier "
                        + "such as MRN, ICD-10 code, or NPI (score: " + score + "/100). "
                        + "Sharing PHI violates HIPAA regulations.");
            }
            return new PolicyDecision(
                Action.REDACT,
                "PHI detected and automatically removed (score: " + score + "/100). "
                    + "Health-related sensitive data has been redacted before sending.");
        }

        // 5. PII → always REDACT
        if (type == RiskType.PII) {
            return new PolicyDecision(
                Action.REDACT,
                "PII detected and automatically removed (score: " + score + "/100). "
                    + "Sensitive personal data has been redacted before sending.");
        }

        // 6. SOURCE_CODE → ALERT
        if (type == RiskType.SOURCE_CODE) {
            return new PolicyDecision(
                Action.ALERT,
                "Source code / SQL detected (score: " + score + "/100). "
                    + "Sharing proprietary code with AI tools may expose intellectual property.");
        }

        // 7-10. Score-based fallthrough
        if (score >= 80) {
            return new PolicyDecision(
                Action.BLOCK,
                "Critical risk detected. Score: " + score + "/100.");
        }
        if (score >= 60) {
            return new PolicyDecision(
                Action.REDACT,
                "High-risk content detected and redacted. Score: " + score + "/100.");
        }
        if (score >= 40) {
            return new PolicyDecision(
                Action.ALERT,
                "Medium-risk content detected. Score: " + score + "/100. Please review before sharing.");
        }

        return new PolicyDecision(
            Action.ALLOW,
            "No significant risk detected.");
    }
}
