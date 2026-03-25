package com.promptguard.controller;

import com.promptguard.model.*;
import com.promptguard.service.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class PromptController {

    private final PromptValidationService validationService;
    private final RiskScoreCalculator     riskScoreCalculator;
    private final PolicyEngine            policyEngine;
    private final RedactionService        redactionService;
    private final AuditService            auditService;

    public PromptController(PromptValidationService validationService,
                            RiskScoreCalculator riskScoreCalculator,
                            PolicyEngine policyEngine,
                            RedactionService redactionService,
                            AuditService auditService) {
        this.validationService   = validationService;
        this.riskScoreCalculator = riskScoreCalculator;
        this.policyEngine        = policyEngine;
        this.redactionService    = redactionService;
        this.auditService        = auditService;
    }

    @PostMapping("/prompts")
    public ResponseEntity<PromptResponse> handlePrompt(@RequestBody PromptRequest request) {
        long start = System.currentTimeMillis();

        List<DetectionResult> detections = validationService.validate(
            request.getPrompt(), request.getUserId(), request.getSubUser());
        RiskScore    riskScore = riskScoreCalculator.calculate(detections);
        PolicyDecision decision = policyEngine.decide(riskScore);

        String finalPrompt = request.getPrompt();
        if (decision.getAction() == Action.REDACT) {
            finalPrompt = redactionService.redact(request.getPrompt(), detections);
        }

        long ms = System.currentTimeMillis() - start;
        
        // 💾 SKIP audit if it is just a test from UI/Extension
        if (!request.isTesting()) {
            auditService.log(request, riskScore, decision, finalPrompt, ms);
        }

        PromptResponse resp = new PromptResponse();
        resp.setAction(decision.getAction());
        resp.setReason(decision.getReason());
        resp.setRedactedPrompt(finalPrompt);
        resp.setRiskScore(riskScore.getTotalScore());
        resp.setRiskLevel(riskScore.getRiskLevel());
        resp.setProcessingTimeMs(ms);

        return ResponseEntity.ok(resp);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "PromptGuard"));
    }

    /** Heartbeat from extension — just acknowledge */
    @PostMapping("/heartbeat")
    public ResponseEntity<Map<String, String>> heartbeat(@RequestBody Map<String, Object> body) {
        String userId      = (String) body.getOrDefault("userId",      "unknown");
        String sub         = (String) body.get("subUser");
        String browserName = (String) body.getOrDefault("browserName", "Unknown");
        
        // Default sub-user if empty/missing
        if (sub == null || sub.trim().isEmpty()) sub = "unknown";
        
        String orgLabel = userId;
        if ("101".equals(userId) || "Telecomm".equalsIgnoreCase(userId) || "kushal-user".equals(userId)) orgLabel = "Telecomm";
        else if ("102".equals(userId) || "Software".equalsIgnoreCase(userId) || "rohan-user".equals(userId)) orgLabel = "Software";
        
        // Force the database's actual org mapping to prevent mismatched displays
        if ("rohan-user".equalsIgnoreCase(sub)) orgLabel = "Software";
        if ("kushal-user".equalsIgnoreCase(sub)) orgLabel = "Telecomm";
        
        System.out.println("[Heartbeat] - " + orgLabel + " , [" + sub + "] , browser=" + browserName);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
