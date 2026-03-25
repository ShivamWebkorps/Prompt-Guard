# 🛡️ PromptGuard — Enterprise AI Firewall

> A Chrome/Edge/Brave/Firefox browser extension that intercepts every prompt sent
> to AI tools (ChatGPT, Gemini, Copilot, Claude), scans for sensitive data,
> and blocks, redacts, or alerts — all logged to a PostgreSQL database with
> a real-time Enterprise Security Dashboard.

---

## 📁 Project Structure

```
promptguard/
├── extension/                     ← Load this folder in Chrome/Edge/Brave
│   ├── manifest.json
│   ├── background.js              ← Service worker: heartbeat, role check, browser detect
│   ├── content.js                 ← Intercepts prompts on AI sites
│   ├── icons/
│   │   ├── icon16.png
│   │   ├── icon48.png
│   │   └── icon128.png
│   └── popup/
│       ├── popup.html             ← Extension popup UI
│       └── popup.js               ← Tabs: Test / Settings / Admin
│
├── frontend-dashboard/
│   └── index.html                 ← Open in any browser — no server needed
│
├── backend/
│   ├── pom.xml
│   └── src/main/
│       ├── resources/
│       │   ├── application.properties
│       │   └── schema.sql
│       └── java/com/promptguard/
│           ├── config/            ← DatabaseInitializer (migration + user seeding)
│           ├── controller/        ← REST API endpoints
│           ├── detector/          ← PII / PHI / Secret / Source Code / Keyword / Org-Keyword engines
│           ├── model/             ← PromptRequest, PromptResponse, RiskType, etc.
│           ├── repository/        ← SQL queries (JdbcTemplate)
│           └── service/           ← AuditService, PolicyEngine, PromptValidationService, etc.
│
└── README.md
```

---

## ✅ Prerequisites

| Tool | Minimum Version | Check Command |
|---|---|---|
| Java | 17 | `java -version` |
| Maven | 3.8 | `mvn -version` |
| PostgreSQL | 13 | `psql --version` |
| Chrome/Edge/Brave | Any | — |

---

## 🚀 Step-by-Step Setup

### STEP 1 — Create PostgreSQL Database

Open **DBeaver** → right-click **Databases** → **Create New Database**

```
Database name:  browser_extension_final
```

> ⚠️ Tables are created **automatically** on first backend start. Do NOT run schema.sql manually.

---

### STEP 2 — Configure Database Password

Open `backend/src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/browser_extension_final
spring.datasource.username=postgres
spring.datasource.password=YOUR_PASSWORD    ← change ONLY this line
```

---

### STEP 3 — Start the Backend

```powershell
cd backend
mvn clean spring-boot:run
```

**✅ Expected console output on first run:**
```
=== PromptGuard DB Init ===
⏭️  browser_name column already exists
✅ Seeded user: admin-user (ADMIN)
✅ Seeded user: rohan-user (USER)
✅ Seeded user: kushal-user (USER)
✅ Tables ready — users: 3, audit_logs: 0
=== DB Init Complete ===
```

**Verify backend is running:**
→ Open: `http://localhost:8080/api/v1/health`
→ Expected: `{"status":"UP","service":"PromptGuard"}`

---

### STEP 4 — Load the Extension in Chrome/Edge/Brave

1. Open browser → address bar → `chrome://extensions` → Enter
2. Enable **Developer mode** toggle (top-right corner)
3. Click **Load unpacked**
4. Select the `extension/` folder
5. 🛡️ PromptGuard icon appears in your toolbar

---

### STEP 5 — Configure the Extension

1. Click the 🛡️ PromptGuard icon in the toolbar
2. Go to **⚙️ Settings** tab
3. Fill in:
   - **User ID** → parent org e.g. `rohan-user` or `kushal-user`
   - **Sub User** → employee name e.g. `user1`
   - **Backend API URL** → `http://localhost:8080`
4. Click **Save Settings**

> ℹ️ **userId = parent organisation** (e.g. `rohan-user`),
> **subUser = actual employee** (e.g. `user1`).
> This determines which `user_keyword_policies` rows are applied.

---

### STEP 6 — Open the Dashboard

1. Open `frontend-dashboard/index.html` in your browser
2. Select your user → **Sign In**

> Only users that exist in the `users` table can log in.

---

## 🔍 Detection Engines — Processing Order

Detectors run in **two phases**. Phase 1 for all users; Phase 2 is org-specific.

```
Phase 1 — Global (same for every user/org)
  ┌──────────────────────────────────────────────┐
  │  1. SecretDetector      API keys, tokens     │
  │  2. PiiDetector         SSN, CC, Aadhaar     │
  │  3. PhiDetector         HIPAA health data    │  ← NEW in v11
  │  4. SourceCodeDetector  SQL / Java / Python  │
  │  5. KeywordDetector     Global block words   │
  └──────────────────────────────────────────────┘
          ↓ if Phase 1 finds nothing risky
Phase 2 — Org-specific (isolated per organisation)
  ┌──────────────────────────────────────────────┐
  │  6. UserKeywordDetector                      │
  │     WHERE user_id = ? AND sub_user = ?       │
  │                                              │
  │     rohan-user's list → rohan's sub-users    │
  │     kushal-user's list → kushal's sub-users  │
  └──────────────────────────────────────────────┘
```

| Detector | What It Detects | Score | Action |
|---|---|---|---|
| `SecretDetector` | API keys, AWS credentials, tokens | 100 | BLOCK |
| `PiiDetector` | SSN, credit card, Aadhaar, PAN, phone, email | 60–75 | REDACT |
| `PhiDetector` | MRN, ICD-10, NPI, medication, diagnosis | 65–80 | BLOCK / REDACT |
| `SourceCodeDetector` | SQL, Java, Python code | 50–70 | ALERT |
| `KeywordDetector` | Global block/alert keywords | 55–100 | BLOCK / ALERT |
| `UserKeywordDetector` | Org-specific keyword_list per sub-user | 75–100 | BLOCK / CRITICAL / REDACT / ALLOW |

---

## 🏥 PHI Detector — HIPAA Safe Harbor

`PhiDetector` follows **HIPAA Safe Harbor** (45 CFR §164.514(b)).

| PHI Type | Example | Score | Action |
|---|---|---|---|
| MRN (Medical Record Number) | `MRN: 789456` | 80 | **BLOCK** |
| ICD-10 diagnosis code | `E11.9`, `J18.9`, `C50.911` | 80 | **BLOCK** |
| NPI (Provider Identifier) | `NPI: 1234567890` | 80 | **BLOCK** |
| Date of birth | `DOB: 15/03/1990` | 75 | REDACT |
| Health insurance/policy ID | `member id: ABC123` | 65 | REDACT |
| Medication name | `metformin 500mg` | 70 | REDACT |
| Diagnosis/clinical terms | `patient diagnosed with`, `lab results` | 70 | REDACT |

> Redaction placeholder: `[REDACTED-PHI]`
> HIPAA violations carry penalties up to $1.9M per category — handled separately from PII.

---

## 🏢 Organisation-Based Keyword Isolation

Each organisation maintains its own keyword policy in `user_keyword_policies`.
Policies are **completely isolated** — one org cannot see or affect another org's checks.

### Column → Action mapping

| Column | Score | Action |
|---|---|---|
| `block_col = true` | 100 | **BLOCK** (absolute) |
| `critial_col = true` | 85 | **BLOCK** (critical severity) |
| `redacted_col = true` | 75 | **REDACT** |
| `allow_col = true` | — | **ALLOW** (whitelist — no detection result added) |

### Real scenario with current DB data

| Prompt | userId | subUser | Result |
|---|---|---|---|
| "confidential strategy" | rohan-user | user1 | **BLOCK** (block_col) |
| "salary for pre-ipo" | rohan-user | user2 | **REDACT** (redacted_col) |
| "merger acquisition" | kushal-user | user1 | **BLOCK** (critial_col, score=85, CRITICAL) |
| "merger acquisition" | rohan-user | user1 | **ALLOW** ← not in rohan's list |
| "confidential report" | kushal-user | user1 | **ALLOW** ← not in kushal's list |

### Adding org policies in DBeaver

```sql
INSERT INTO user_keyword_policies
  (user_id, sub_user, keyword_list, block_col, prompt_col)
VALUES
  ('rohan-user', 'user1', 'confidential,secret', true, 'Block sensitive leaks');

INSERT INTO user_keyword_policies
  (user_id, sub_user, keyword_list, critial_col, prompt_col)
VALUES
  ('kushal-user', 'user1', 'merger,acquisition', true, 'Telecom M&A protection');
```

---

## ⚙️ Policy Actions

| Action | Risk Level | What Happens | User Sees |
|---|---|---|---|
| `ALLOW` | NONE / LOW | Prompt sent through silently | Nothing |
| `ALERT` | MEDIUM | Prompt sent + warning shown | ⚠️ Orange toast |
| `REDACT` | HIGH | Sensitive text removed, rest sent | ✏️ Purple toast |
| `BLOCK` | CRITICAL | Prompt completely stopped | 🚫 Red toast |

### Redaction placeholders

| Risk Type | Placeholder |
|---|---|
| PII | `[REDACTED-PII]` |
| PHI | `[REDACTED-PHI]` |
| Secret | `[REDACTED-SECRET]` |
| Org keyword (redact) | `[REDACTED-ORG]` |
| Source code | `[REDACTED-CODE]` |

---

## 👥 Default Users

| User ID | Name | Role | Access |
|---|---|---|---|
| `admin-user` | Admin | ADMIN | Full dashboard — all users |
| `rohan-user` | Rohan | USER — Software org | Own logs only |
| `kushal-user` | Kushal | USER — Telecom org | Own logs only |

---

## 🗄️ Database Schema

```sql
CREATE TABLE users (
    user_id      VARCHAR(100) PRIMARY KEY,
    display_name VARCHAR(200),
    role         VARCHAR(20) CHECK (role IN ('ADMIN','USER')),
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE audit_logs (
    id                 UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            VARCHAR(100) NOT NULL,
    tool               VARCHAR(100),
    browser_name       VARCHAR(50),
    original_prompt    TEXT,
    redacted_prompt    TEXT,
    highest_risk_type  VARCHAR(50),  -- SECRET|PII|PHI|ORG_KEYWORD|SOURCE_CODE|KEYWORD|NONE
    risk_score         INTEGER,
    risk_level         VARCHAR(20),
    action             VARCHAR(20),
    action_reason      TEXT,
    processing_time_ms BIGINT,
    created_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE user_keyword_policies (
    id            SERIAL       PRIMARY KEY,
    user_id       VARCHAR(100) NOT NULL,   -- Parent org  e.g. rohan-user
    sub_user      VARCHAR(100) NOT NULL,   -- Employee    e.g. user1
    keyword_list  TEXT         NOT NULL,   -- Comma-separated e.g. "confidential,secret"
    allow_col     BOOLEAN      DEFAULT FALSE,
    redacted_col  BOOLEAN      DEFAULT FALSE,
    critial_col   BOOLEAN      DEFAULT FALSE,
    block_col     BOOLEAN      DEFAULT FALSE,
    prompt_col    TEXT
);
```

---

## 🌐 API Reference

### POST `/api/v1/prompts`
```json
Request:
{
  "userId":      "rohan-user",
  "subUser":     "user1",
  "tool":        "ChatGPT",
  "browserName": "Chrome",
  "prompt":      "Patient MRN: 123456 has E11.9",
  "timestamp":   "2026-03-19T12:00:00"
}
Response:
{
  "action":           "BLOCK",
  "reason":           "PHI detected — MRN (Medical Record Number) (score: 80/100)",
  "riskScore":        80,
  "riskLevel":        "CRITICAL",
  "redactedPrompt":   "Patient MRN: 123456 has E11.9",
  "processingTimeMs": 12
}
```

---

## 🧪 Test Cases

### TC-01 — Safe Prompt → ALLOW ✅
```
Prompt:   How do I reverse a string in Python?
Expected: No toast. Prompt sent normally.
DB:       action=ALLOW, risk_score=0
```

### TC-02 — SSN → REDACT ✏️
```
Prompt:   My SSN is 123-45-6789
Expected: Purple toast, [REDACTED-PII]
DB:       highest_risk_type=PII
```

### TC-03 — AWS Key → BLOCK 🚫
```
Prompt:   My AWS key is AKIAIOSFODNN7EXAMPLE
Expected: Red toast — BLOCK
DB:       highest_risk_type=SECRET
```

### TC-04 — PHI: ICD-10 code → BLOCK 🚫
```
Prompt:   Patient E11.9 needs insulin adjustment
Expected: BLOCK — PHI ICD-10 code detected (score=80)
DB:       highest_risk_type=PHI
```

### TC-05 — PHI: Medication → REDACT ✏️
```
Prompt:   Patient is on metformin 500mg twice daily
Expected: [REDACTED-PHI], purple toast
DB:       highest_risk_type=PHI, action=REDACT
```

### TC-06 — Org keyword: rohan-user/user1 → BLOCK 🚫
```
userId=rohan-user, subUser=user1
Prompt:   Share the confidential strategy
Expected: BLOCK (block_col=true)
DB:       highest_risk_type=ORG_KEYWORD
```

### TC-07 — Same prompt, different org → ALLOW ✅
```
userId=kushal-user, subUser=user1
Prompt:   Share the confidential strategy
Expected: ALLOW (not in kushal's keyword_list)
DB:       action=ALLOW
```

### TC-08 — kushal-user/user1 M&A → CRITICAL BLOCK 🚫
```
userId=kushal-user, subUser=user1
Prompt:   Discuss the merger acquisition plan
Expected: BLOCK (critial_col=true, score=85, risk_level=CRITICAL)
DB:       highest_risk_type=ORG_KEYWORD, risk_score=85
```

### TC-09 — allow_col whitelist → ALLOW ✅
```
Org has allow_col=true for a keyword
Prompt:   Contains the whitelisted keyword
Expected: ALLOW — no detection result, prompt passes
```

---

## 🔧 Troubleshooting

| Problem | Fix |
|---|---|
| Org keywords not triggering | Settings → set correct userId (parent org) + subUser (employee) |
| PHI not detecting | Ensure ICD code format is letter+2digits e.g. `E11.9` |
| allow_col keyword still blocking | Upgrade to pg_v11 — bug fixed |
| `browser=Chrome` in Brave | Already fixed in this version |
| Dashboard shows no users | Run `mvn clean spring-boot:run` |

---

## 🏗️ Tech Stack

| Layer | Technology |
|---|---|
| Extension | Chrome Manifest V3 |
| Dashboard | React 18 + Chart.js 4.4 (CDN, no build) |
| Backend | Spring Boot 3.2, Java 17 |
| Database | PostgreSQL 13+ |
| DB Layer | HikariCP + JdbcTemplate (no JPA) |
| PHI Standard | HIPAA Safe Harbor — 45 CFR §164.514(b) |

---

## 📝 Changelog

### pg_v11 (current)

- **NEW** `PhiDetector.java` — HIPAA PHI detection (MRN, ICD-10, NPI, medication, diagnosis)
- **NEW** `RiskType.PHI` and `RiskType.ORG_KEYWORD` — separate audit trail per risk category
- `critial_col` correctly maps to BLOCK with RiskLevel=CRITICAL (score=85), distinct from block_col (score=100)
- **FIXED** `allow_col=true` bug — now silently skips without adding a detection result
- **UPDATED** `PolicyEngine` — ORG_KEYWORD and PHI branches added with correct priority
- **UPDATED** `RedactionService` — `[REDACTED-PHI]` and `[REDACTED-ORG]` placeholders
- **UPDATED** `PromptValidationService` — Phase 1 (global) / Phase 2 (org) clearly separated

### pg_v10
- Initial release with 4 detectors, browser detection, admin dashboard

---

*PromptGuard v11 — Enterprise AI Security with HIPAA PHI Detection & Org Isolation*
