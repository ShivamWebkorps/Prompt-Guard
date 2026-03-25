package com.promptguard.detector;

import com.promptguard.model.DetectionResult;
import com.promptguard.model.RiskType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PiiDetector — detects Personally Identifiable Information.
 *
 * All PII → score set so PolicyEngine routes to REDACT:
 *   EMAIL        score=60  → HIGH  → REDACT
 *   PHONE (IN)   score=65  → HIGH  → REDACT
 *   AADHAAR      score=70  → HIGH  → REDACT
 *   PAN          score=70  → HIGH  → REDACT
 *   SSN (US)     score=75  → HIGH  → REDACT
 *   CREDIT CARD  score=75  → HIGH  → REDACT
 *
 * IMPORTANT: All scores kept BELOW 80 so RiskLevel stays HIGH (not CRITICAL).
 * PolicyEngine checks PII type FIRST and always returns REDACT regardless,
 * but keeping scores < 80 is defence-in-depth.
 */
@Component
public class PiiDetector {

    // Email address
    private static final Pattern EMAIL = Pattern.compile(
        "[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}");

    // Indian mobile numbers (+91 optional, starts 6-9, 10 digits)
    private static final Pattern PHONE = Pattern.compile(
        "(\\+91[\\-\\s]?)?[6-9]\\d{9}");

    // Aadhaar: 12-digit number in groups of 4 separated by space/hyphen
    private static final Pattern AADHAAR = Pattern.compile(
        "\\b[2-9]\\d{3}[\\s\\-]?\\d{4}[\\s\\-]?\\d{4}\\b");

    // Indian PAN: 5 letters + 4 digits + 1 letter
    private static final Pattern PAN = Pattern.compile(
        "\\b[A-Z]{5}[0-9]{4}[A-Z]\\b");

    // US Social Security Number: 3-2-4 digit format
    private static final Pattern SSN = Pattern.compile(
        "\\b\\d{3}-\\d{2}-\\d{4}\\b");

    // Credit card: 13-16 digits, optionally separated by space or hyphen
    // Uses tighter word-boundary approach to avoid matching plain phone numbers
    private static final Pattern CREDIT = Pattern.compile(
        "\\b(?:4[0-9]{12}(?:[0-9]{3})?|" +          // Visa 13/16
        "5[1-5][0-9]{14}|" +                          // MasterCard
        "3[47][0-9]{13}|" +                           // Amex 15
        "6(?:011|5[0-9]{2})[0-9]{12}|" +             // Discover
        "(?:\\d[ -]?){13,16})\\b");                   // generic spaced

    public List<DetectionResult> detect(String prompt) {
        List<DetectionResult> results = new ArrayList<>();

        check(prompt, EMAIL,   "EMAIL",       60, results);
        check(prompt, PHONE,   "PHONE",       65, results);
        check(prompt, AADHAAR, "AADHAAR",     70, results);
        check(prompt, PAN,     "PAN_NUMBER",  70, results);
        check(prompt, SSN,     "SSN",         75, results);
        check(prompt, CREDIT,  "CREDIT_CARD", 75, results);

        return results;
    }

    private void check(String prompt, Pattern p, String label, int score,
                       List<DetectionResult> results) {
        Matcher m = p.matcher(prompt);
        if (m.find()) {
            results.add(new DetectionResult(
                RiskType.PII,
                score,
                "PII detected: " + label,
                m.group()   // the matched value used by RedactionService
            ));
        }
    }
}
