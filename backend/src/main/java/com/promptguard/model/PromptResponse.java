package com.promptguard.model;

public class PromptResponse {

    private Action    action;
    private String    reason;
    private String    redactedPrompt;
    private int       riskScore;
    private RiskLevel riskLevel;
    private long      processingTimeMs;

    public Action    getAction()                   { return action; }
    public void      setAction(Action v)           { this.action = v; }

    public String    getReason()                   { return reason; }
    public void      setReason(String v)           { this.reason = v; }

    public String    getRedactedPrompt()           { return redactedPrompt; }
    public void      setRedactedPrompt(String v)   { this.redactedPrompt = v; }

    public int       getRiskScore()                { return riskScore; }
    public void      setRiskScore(int v)           { this.riskScore = v; }

    public RiskLevel getRiskLevel()                { return riskLevel; }
    public void      setRiskLevel(RiskLevel v)     { this.riskLevel = v; }

    public long      getProcessingTimeMs()         { return processingTimeMs; }
    public void      setProcessingTimeMs(long v)   { this.processingTimeMs = v; }
}
