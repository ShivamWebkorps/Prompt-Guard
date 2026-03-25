package com.promptguard.service;

import com.promptguard.model.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private final JdbcTemplate db;
    private final TokenService tokenService;

    public AuditService(JdbcTemplate db, TokenService tokenService) { 
        this.db = db; 
        this.tokenService = tokenService;
    }

    @Async
    public void log(PromptRequest request,
                    RiskScore riskScore,
                    PolicyDecision decision,
                    String finalPrompt,
                    long processingTimeMs) {

        String userId = (request.getUserId() != null && !request.getUserId().isBlank())
                ? request.getUserId().trim()
                : "anonymous-user";

        try {
            String riskType = "NONE";
            if (riskScore != null && riskScore.getRiskType() != null)
                riskType = riskScore.getRiskType().name();

            String riskLevel = "NONE";
            if (riskScore != null && riskScore.getRiskLevel() != null)
                riskLevel = riskScore.getRiskLevel().name();

            int score  = (riskScore != null) ? riskScore.getTotalScore() : 0;

            String action = "ALLOW";
            if (decision != null && decision.getAction() != null)
                action = decision.getAction().name();

            String reason = (decision != null) ? decision.getReason() : "";

            // Calculate Token/Cost Metrics
            String originalPrompt = request.getPrompt();
            String tool = request.getTool() != null ? request.getTool() : "Unknown";
            
            int inputOri = tokenService.countTokens(originalPrompt);
            int outputOri = tokenService.getEstimateResponseTokens(originalPrompt);
            double costOri = tokenService.calculateCost(tool, inputOri, outputOri);
            
            int tkUsed = 0;
            int tkSaved = 0;
            double cUsed = 0.0;
            double cSaved = 0.0;
            
            if ("BLOCK".equals(action)) {
                tkSaved = inputOri + outputOri;
                cSaved = costOri;
            } else if ("REDACT".equals(action)) {
                int inputRed = tokenService.countTokens(finalPrompt);
                int outputRed = tokenService.getEstimateResponseTokens(finalPrompt);
                tkUsed = inputRed + outputRed;
                cUsed = tokenService.calculateCost(tool, inputRed, outputRed);
                tkSaved = Math.max(0, (inputOri + outputOri) - tkUsed);
                cSaved = Math.max(0, costOri - cUsed);
            } else {
                tkUsed = inputOri + outputOri;
                cUsed = costOri;
            }

            db.update(
                "INSERT INTO audit_logs " +
                "(user_id, tool, browser_name, original_prompt, redacted_prompt, " +
                " highest_risk_type, risk_score, risk_level, action, action_reason, " +
                " processing_time_ms, tokens_used, tokens_saved, cost_used, cost_saved) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                userId,
                tool,
                request.getBrowserName() != null ? request.getBrowserName() : "Unknown",
                originalPrompt,
                finalPrompt,
                riskType,
                score,
                riskLevel,
                action,
                reason,
                processingTimeMs,
                tkUsed,
                tkSaved,
                cUsed,
                cSaved
            );

            String sub = (request.getSubUser() != null && !request.getSubUser().trim().isEmpty()) ? request.getSubUser().trim() : "unknown";

            String orgLabel = userId;
            if ("101".equals(userId) || "Telecomm".equalsIgnoreCase(userId) || "kushal-user".equals(userId)) orgLabel = "Telecomm";
            else if ("102".equals(userId) || "Software".equalsIgnoreCase(userId) || "rohan-user".equals(userId)) orgLabel = "Software";
            
            // Force the database's actual org mapping to prevent mismatched displays
            if ("rohan-user".equalsIgnoreCase(sub)) orgLabel = "Software";
            if ("kushal-user".equalsIgnoreCase(sub)) orgLabel = "Telecomm";

            System.out.println("[AuditService] ✅ Saved ✅ Organization=" + orgLabel + ",user=" + sub
                + ", tool=" + request.getTool()
                + ", browser=" + request.getBrowserName()
                + ", action=" + action);

        } catch (Exception e) {
            System.err.println("[AuditService] ❌ Failed to save log: " + e.getMessage());
            System.err.println("[AuditService] userId=" + userId + ", tool=" + request.getTool());
        }
    }
}
