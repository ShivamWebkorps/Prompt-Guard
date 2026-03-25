package com.promptguard.model;

import java.util.List;

public class RiskScore {

    private int       totalScore;   // 0-100
    private RiskLevel riskLevel;    // CRITICAL / HIGH / MEDIUM / LOW / NONE
    private RiskType  riskType;     // Highest risk type found (used in AuditService)
    private List<DetectionResult> detections;

    public RiskScore() {}

    public RiskScore(int totalScore, RiskLevel riskLevel, RiskType riskType,
                     List<DetectionResult> detections) {
        this.totalScore  = totalScore;
        this.riskLevel   = riskLevel;
        this.riskType    = riskType;
        this.detections  = detections;
    }

    public int       getTotalScore()              { return totalScore; }
    public void      setTotalScore(int v)         { this.totalScore = v; }

    public RiskLevel getRiskLevel()               { return riskLevel; }
    public void      setRiskLevel(RiskLevel v)    { this.riskLevel = v; }

    // AuditService uses getRiskType() — this is the correct method name
    public RiskType  getRiskType()                { return riskType; }
    public void      setRiskType(RiskType v)      { this.riskType = v; }

    public List<DetectionResult> getDetections()  { return detections; }
    public void setDetections(List<DetectionResult> v) { this.detections = v; }
}
