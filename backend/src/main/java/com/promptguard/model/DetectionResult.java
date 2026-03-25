package com.promptguard.model;

public class DetectionResult {

    private RiskType riskType;
    private int      score;        // 0-100
    private String   description;
    private String   matchedValue; // What was detected (for redaction)

    public DetectionResult() {}

    public DetectionResult(RiskType riskType, int score, String description, String matchedValue) {
        this.riskType     = riskType;
        this.score        = score;
        this.description  = description;
        this.matchedValue = matchedValue;
    }

    public RiskType getRiskType()           { return riskType; }
    public void     setRiskType(RiskType v) { this.riskType = v; }

    public int      getScore()              { return score; }
    public void     setScore(int v)         { this.score = v; }

    public String   getDescription()        { return description; }
    public void     setDescription(String v){ this.description = v; }

    public String   getMatchedValue()       { return matchedValue; }
    public void     setMatchedValue(String v){ this.matchedValue = v; }
}
