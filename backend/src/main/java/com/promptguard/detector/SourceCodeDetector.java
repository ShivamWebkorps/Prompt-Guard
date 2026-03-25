package com.promptguard.detector;

import com.promptguard.model.DetectionResult;
import com.promptguard.model.RiskType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SourceCodeDetector — detects SQL, Java, and Python code in prompts.
 *
 * All scores kept BELOW 80 so PolicyEngine routes to ALERT (not BLOCK):
 *   SQL query      → score=55 → MEDIUM → ALERT
 *   Python code    → score=50 → MEDIUM → ALERT
 *   Java import    → score=50 → MEDIUM → ALERT
 *   Java class     → score=65 → HIGH   → ALERT
 *   Java annotation→ score=60 → HIGH   → ALERT
 *
 * PolicyEngine checks SOURCE_CODE type explicitly and always returns ALERT.
 */
@Component
public class SourceCodeDetector {

    // ── Java ──────────────────────────────────────────────────────────────
    private static final Pattern JAVA_CLASS = Pattern.compile(
        "\\b(public|private|protected)\\s+(class|interface|enum)\\s+\\w+");
    private static final Pattern JAVA_IMPORT = Pattern.compile(
        "^import\\s+[a-z]+(\\.[a-zA-Z]+)+;", Pattern.MULTILINE);
    private static final Pattern JAVA_ANNOTATION = Pattern.compile(
        "@(Autowired|Service|Repository|Controller|RestController|Component|Entity|Bean)");

    // ── SQL ───────────────────────────────────────────────────────────────
    private static final Pattern SQL_SELECT = Pattern.compile(
        "\\bSELECT\\s+.{1,200}\\s+FROM\\s+\\w+", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern SQL_INSERT = Pattern.compile(
        "\\bINSERT\\s+INTO\\s+\\w+", Pattern.CASE_INSENSITIVE);
    private static final Pattern SQL_UPDATE = Pattern.compile(
        "\\bUPDATE\\s+\\w+\\s+SET\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SQL_DELETE = Pattern.compile(
        "\\bDELETE\\s+FROM\\s+\\w+", Pattern.CASE_INSENSITIVE);
    private static final Pattern SQL_DROP = Pattern.compile(
        "\\bDROP\\s+(TABLE|DATABASE|SCHEMA)\\s+\\w+", Pattern.CASE_INSENSITIVE);

    // ── Python ────────────────────────────────────────────────────────────
    private static final Pattern PYTHON_DEF = Pattern.compile(
        "^def\\s+\\w+\\s*\\(", Pattern.MULTILINE);
    private static final Pattern PYTHON_IMPORT = Pattern.compile(
        "^(import|from)\\s+\\w+", Pattern.MULTILINE);

    public List<DetectionResult> detect(String prompt) {
        List<DetectionResult> results = new ArrayList<>();

        // Java class / interface / enum
        if (JAVA_CLASS.matcher(prompt).find()) {
            results.add(new DetectionResult(RiskType.SOURCE_CODE, 65,
                "Source code detected: Java class/interface",
                snippet(prompt, JAVA_CLASS)));
        }
        // Java Spring annotations
        if (JAVA_ANNOTATION.matcher(prompt).find()) {
            results.add(new DetectionResult(RiskType.SOURCE_CODE, 60,
                "Source code detected: Java annotation",
                snippet(prompt, JAVA_ANNOTATION)));
        }
        // Java imports
        if (JAVA_IMPORT.matcher(prompt).find()) {
            results.add(new DetectionResult(RiskType.SOURCE_CODE, 50,
                "Source code detected: Java import",
                "import statement"));
        }
        // SQL SELECT
        if (SQL_SELECT.matcher(prompt).find()) {
            results.add(new DetectionResult(RiskType.SOURCE_CODE, 55,
                "Source code detected: SQL SELECT query",
                "SQL query"));
        }
        // SQL INSERT
        if (SQL_INSERT.matcher(prompt).find()) {
            results.add(new DetectionResult(RiskType.SOURCE_CODE, 55,
                "Source code detected: SQL INSERT query",
                "SQL query"));
        }
        // SQL UPDATE
        if (SQL_UPDATE.matcher(prompt).find()) {
            results.add(new DetectionResult(RiskType.SOURCE_CODE, 55,
                "Source code detected: SQL UPDATE query",
                "SQL query"));
        }
        // SQL DELETE / DROP — slightly higher since destructive
        if (SQL_DELETE.matcher(prompt).find() || SQL_DROP.matcher(prompt).find()) {
            results.add(new DetectionResult(RiskType.SOURCE_CODE, 70,
                "Source code detected: destructive SQL (DELETE/DROP)",
                "SQL query"));
        }
        // Python function definition
        if (PYTHON_DEF.matcher(prompt).find()) {
            results.add(new DetectionResult(RiskType.SOURCE_CODE, 50,
                "Source code detected: Python function",
                snippet(prompt, PYTHON_DEF)));
        }
        // Python imports
        if (PYTHON_IMPORT.matcher(prompt).find()) {
            results.add(new DetectionResult(RiskType.SOURCE_CODE, 50,
                "Source code detected: Python import",
                "Python import"));
        }

        return results;
    }

    private String snippet(String text, Pattern p) {
        Matcher m = p.matcher(text);
        if (m.find()) {
            String match = m.group();
            return match.substring(0, Math.min(60, match.length()));
        }
        return "";
    }
}
