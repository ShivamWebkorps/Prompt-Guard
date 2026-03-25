package com.promptguard.service;

import com.promptguard.model.*;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class RiskScoreCalculator {

    public RiskScore calculate(List<DetectionResult> detections) {
        if (detections == null || detections.isEmpty()) {
            return new RiskScore(0, RiskLevel.NONE, RiskType.NONE, detections);
        }

        // Highest individual score becomes the total score (capped at 100)
        int totalScore = detections.stream()
                .mapToInt(DetectionResult::getScore)
                .max()
                .orElse(0);
        totalScore = Math.min(totalScore, 100);

        // Highest risk type = the one with the highest score
        RiskType highestType = detections.stream()
                .max(Comparator.comparingInt(DetectionResult::getScore))
                .map(DetectionResult::getRiskType)
                .orElse(RiskType.NONE);

        RiskLevel level = scoreToLevel(totalScore);

        return new RiskScore(totalScore, level, highestType, detections);
    }

    private RiskLevel scoreToLevel(int score) {
        if (score >= 80) return RiskLevel.CRITICAL;
        if (score >= 60) return RiskLevel.HIGH;
        if (score >= 40) return RiskLevel.MEDIUM;
        if (score > 0)   return RiskLevel.LOW;
        return RiskLevel.NONE;
    }
}
