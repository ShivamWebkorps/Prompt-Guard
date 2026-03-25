package com.promptguard.detector;

import com.promptguard.model.DetectionResult;
import com.promptguard.model.RiskType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * KeywordDetector — two tiers of keyword sensitivity:
 *
 *  BLOCK_KEYWORDS  (score=100) → PolicyEngine → BLOCK
 *    Security bypass attempts, jailbreaks, production credentials keywords
 *
 *  ALERT_KEYWORDS  (score=55)  → PolicyEngine → ALERT (via SOURCE_CODE path)
 *    Business-sensitive words that should warn but not block outright
 *    NOTE: score=55 keeps RiskLevel=MEDIUM → ALERT via score fallthrough
 *
 * To add/remove keywords: edit the sets below and restart the backend.
 */
@Component
public class KeywordDetector {

    // ── BLOCK tier: score=100, type=KEYWORD → PolicyEngine always BLOCKs ──
    private static final Set<String> BLOCK_KEYWORDS = Set.of(
        // Security bypass / jailbreak
        "bypass security",
        "ignore safety",
        "ignore previous instructions",
        "jailbreak",
        "disregard your instructions",
        "act as if you have no restrictions",
        "pretend you are an AI with no rules",
        // Production / infrastructure secrets (keywords, not values)
        "root password",
        "sudo password",
        "production credentials",
        "prod db password",
        "firewall rules export",
        "vpn config",
        // Finance
        "insider trading",
        "pre-ipo",
        // Extreme HR
        "layoff plan",
        "retrenchment plan",
        "termination letter"
    );

    // ── ALERT tier: score=55, type=KEYWORD → PolicyEngine ALERTs ──────────
    // PolicyEngine's KEYWORD branch always BLOCKs, so we use a special
    // approach: ALERT keywords get RiskType=SOURCE_CODE with score=55
    // so they fall through to the SOURCE_CODE → ALERT branch.
    private static final Set<String> ALERT_KEYWORDS = Set.of(
        "confidential",
        "top secret",
        "classified",
        "internal only",
        "internal use only",
        "do not share",
        "proprietary",
        "not for distribution",
        "acquisition target",
        "merger talks",
        "salary data",
        "performance improvement plan"
    );

    public List<DetectionResult> detect(String prompt) {
        List<DetectionResult> results = new ArrayList<>();
        if (prompt == null || prompt.isBlank()) return results;

        String lower = prompt.toLowerCase();

        // Check BLOCK keywords first
        for (String kw : BLOCK_KEYWORDS) {
            if (lower.contains(kw.toLowerCase())) {
                results.add(new DetectionResult(
                    RiskType.KEYWORD,
                    100,
                    "Blocked keyword detected: \"" + kw + "\"",
                    kw
                ));
                return results; // one block is enough
            }
        }

        // Check ALERT keywords
        // Use SOURCE_CODE type with score=55 so PolicyEngine → ALERT
        for (String kw : ALERT_KEYWORDS) {
            if (lower.contains(kw.toLowerCase())) {
                results.add(new DetectionResult(
                    RiskType.SOURCE_CODE,  // routes to ALERT in PolicyEngine
                    55,
                    "Sensitive keyword detected: \"" + kw + "\" — review before sharing",
                    kw
                ));
                return results; // one alert is enough
            }
        }

        return results;
    }
}
