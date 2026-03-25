package com.promptguard.detector;

import com.promptguard.model.DetectionResult;
import com.promptguard.model.RiskType;
import com.promptguard.model.UserKeywordPolicy;
import com.promptguard.repository.UserPolicyRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * UserKeywordDetector — Org-specific keyword check.
 *
 * HOW IT WORKS:
 *   1. Fetch only the rows where user_id = parent-org AND sub_user = current employee
 *   2. Check if prompt contains any keyword from keyword_list
 *   3. Apply the action defined by the DB column checked for that policy row
 *
 * ISOLATION GUARANTEE:
 *   rohan-user's keyword_list is NEVER checked for kushal-user's sub-users.
 *   The DB query uses WHERE user_id = ? AND sub_user = ? — fully isolated per org.
 *
 * ALL 4 COLUMN → ACTION mappings:
 *
 *   block_col    = true  →  score=100  →  Action=BLOCK,  RiskLevel=CRITICAL
 *   critial_col  = true  →  score=85   →  Action=ALERT,  RiskLevel=CRITICAL  ← FIXED
 *   redacted_col = true  →  score=75   →  Action=REDACT, RiskLevel=HIGH
 *   allow_col    = true  →  no result  →  prompt passes through (ALLOW)
 *
 * BLOCK vs CRITICAL:
 *   block_col   → absolute block (score=100), prompt cannot be sent
 *   critial_col → critical alert (score=85), flagged as ALERT so user sees yellow warning
 *   block_col results in Action.BLOCK. critial_col results in Action.ALERT.
 */
@Component
public class UserKeywordDetector {

    private final UserPolicyRepository repository;

    public UserKeywordDetector(UserPolicyRepository repository) {
        this.repository = repository;
    }

    public List<DetectionResult> detect(String userId, String subUser, String prompt) {
        List<DetectionResult> results = new ArrayList<>();
        if (prompt == null || prompt.isBlank()) return results;
        if (userId == null || subUser == null)  return results;

        List<UserKeywordPolicy> policies = repository.findPolicies(userId, subUser);
        String lowerPrompt = prompt.toLowerCase();

        for (UserKeywordPolicy policy : policies) {
            String[] keywords = policy.getKeywordList().split(",");

            for (String kw : keywords) {
                String cleanKw = kw.trim();
                if (cleanKw.isEmpty()) continue;

                if (cleanKw.equals("*") || lowerPrompt.contains(cleanKw.toLowerCase())) {

                    // allow_col = true → explicitly whitelisted → no result, prompt passes
                    if (policy.isAllowCol()) {
                        break;
                    }

                    int    score     = 0;
                    String actionStr = "NONE";

                    if (policy.isBlockCol()) {
                        score     = 100;   // → PolicyEngine BLOCK (RiskLevel=CRITICAL)
                        actionStr = "BLOCK";
                    } else if (policy.isCriticalCol()) {
                        score     = 85;    // → PolicyEngine BLOCK (RiskLevel=CRITICAL)
                        actionStr = "CRITICAL";
                    } else if (policy.isRedactedCol()) {
                        score     = 75;    // → PolicyEngine REDACT (RiskLevel=HIGH)
                        actionStr = "REDACT";
                    }

                    if (score > 0) {
                        results.add(new DetectionResult(
                            RiskType.ORG_KEYWORD,
                            score,
                            "Org-specific keyword hit (" + actionStr + ") "
                                + "for org [" + userId + "]: \"" + cleanKw + "\"",
                            cleanKw
                        ));
                    }

                    break;
                }
            }
        }

        return results;
    }
}
