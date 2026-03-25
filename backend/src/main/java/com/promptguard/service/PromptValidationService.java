package com.promptguard.service;

import com.promptguard.detector.*;
import com.promptguard.model.DetectionResult;
import com.promptguard.model.User;
import com.promptguard.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class PromptValidationService {

    private final SecretDetector      secretDetector;
    private final PiiDetector         piiDetector;
    private final PhiDetector         phiDetector;
    private final SourceCodeDetector  sourceCodeDetector;
    private final KeywordDetector     keywordDetector;
    private final UserKeywordDetector userKeywordDetector;
    private final UserRepository      userRepository;

    public PromptValidationService(SecretDetector secretDetector,
                                   PiiDetector piiDetector,
                                   PhiDetector phiDetector,
                                   SourceCodeDetector sourceCodeDetector,
                                   KeywordDetector keywordDetector,
                                   UserKeywordDetector userKeywordDetector,
                                   UserRepository userRepository) {
        this.secretDetector      = secretDetector;
        this.piiDetector         = piiDetector;
        this.phiDetector         = phiDetector;
        this.sourceCodeDetector  = sourceCodeDetector;
        this.keywordDetector     = keywordDetector;
        this.userKeywordDetector = userKeywordDetector;
        this.userRepository      = userRepository;
    }

    public List<DetectionResult> validate(String prompt, String userId, String subUser) {
        List<DetectionResult> all = new ArrayList<>();

        // ── PHASE 1: Global detectors — same rules for ALL users/orgs ────────
        all.addAll(secretDetector.detect(prompt));
        all.addAll(piiDetector.detect(prompt));
        all.addAll(phiDetector.detect(prompt));
        all.addAll(sourceCodeDetector.detect(prompt));
        all.addAll(keywordDetector.detect(prompt));

        // ── PHASE 2: Org-specific keyword check ───────────────────────────────
        // Resolve userId → org_id so policy lookup uses the org_id stored in
        // user_keyword_policies.user_id (e.g. "101" or "102").
        // Falls back to the raw userId string if org_id is not set (admin, unknown).
        String orgKey = userId; // default fallback
        if (userId != null && !userId.isBlank()) {
            try {
                Optional<User> userOpt = userRepository.findByUserId(userId);
                if (userOpt.isPresent() && userOpt.get().getOrgId() != null) {
                    orgKey = String.valueOf(userOpt.get().getOrgId());
                }
            } catch (Exception e) {
                // keep fallback
            }
        }

        all.addAll(userKeywordDetector.detect(orgKey, subUser, prompt));

        return all;
    }
}
