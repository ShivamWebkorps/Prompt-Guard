package com.promptguard.detector;

import com.promptguard.model.DetectionResult;
import com.promptguard.model.RiskType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SecretDetector {

    // Patterns that indicate secrets/credentials
    private static final List<Pattern> SECRET_PATTERNS = List.of(
            Pattern.compile("password\\s*[=:]\\s*\\S+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("passwd\\s*[=:]\\s*\\S+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("pwd\\s*[=:]\\s*\\S+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("api[_-]?key\\s*[=:]\\s*\\S+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("secret[_-]?key\\s*[=:]\\s*\\S+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("access[_-]?token\\s*[=:]\\s*\\S+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("auth[_-]?token\\s*[=:]\\s*\\S+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("bearer\\s+[A-Za-z0-9\\-._~+/]+=*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("private[_-]?key\\s*[=:]\\s*\\S+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("jdbc:[a-z]+://[^\\s]*password=[^\\s&]+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("-----BEGIN (RSA |EC |)PRIVATE KEY-----"),
            Pattern.compile("ghp_[A-Za-z0-9]{36}"), // GitHub token
            Pattern.compile("sk-[A-Za-z0-9]{48}"), // OpenAI key
            Pattern.compile("AKIA[0-9A-Z]{16}") // AWS key
    );

    public List<DetectionResult> detect(String prompt) {
        List<DetectionResult> results = new ArrayList<>();
        for (Pattern pattern : SECRET_PATTERNS) {
            Matcher m = pattern.matcher(prompt);
            if (m.find()) {
                results.add(new DetectionResult(
                        RiskType.SECRET,
                        100,
                        "Secret detected: " + classifySecret(m.group()),
                        m.group()));
            }
        }
        return results;
    }

    private String classifySecret(String match) {
        String lower = match.toLowerCase();
        if (lower.contains("password") || lower.contains("passwd") || lower.contains("pwd"))
            return "PASSWORD";
        if (lower.contains("api") || lower.contains("key"))
            return "API_KEY";
        if (lower.contains("token"))
            return "TOKEN";
        if (lower.contains("jdbc"))
            return "DB_CREDENTIAL";
        return "SECRET";
    }
}
