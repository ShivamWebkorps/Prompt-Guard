import random
import math
import os
from datetime import datetime, timedelta

# Provider Rates per 1M tokens (from TokenService.java)
RATES = {
    "ChatGPT": {"in": 2.50, "out": 10.00},
    "Claude": {"in": 3.00, "out": 15.00},
    "Gemini": {"in": 3.50, "out": 10.50},
    "Cursor": {"in": 3.00, "out": 12.00}
}

def count_tokens(text):
    return math.ceil(len(text) / 4.0)

def estimate_output(prompt, input_tokens):
    p = prompt.lower()
    estimate = max(300, input_tokens * 3)
    if any(x in p for x in ["essay", "article", "blog", "story", "detailed", "comprehensive"]):
        estimate = max(1200, estimate * 2)
    if any(x in p for x in ["code", "function", "implement", "script", "program", "class"]):
        estimate = max(600, estimate + 400)
    if any(x in p for x in ["summarize", "tldr", "fix", "correct", "rephrase", "shorten"]):
        estimate = min(estimate, max(150, input_tokens // 2))
    return min(4000, max(100, estimate))

def get_cost(tool, in_tk, out_tk):
    rates = RATES.get(tool, RATES["Cursor"])
    return (in_tk * rates["in"] / 1_000_000.0) + (out_tk * rates["out"] / 1_000_000.0)

users_data = [
    ("rohan", "Software", 102),
    ("shivam", "Software", 102),
    ("kushal", "Telecomm", 101),
    ("raj", "Telecomm", 101)
]

prompts_data = [
    ("Write a Python function to sort a list.", "ALLOW", "NONE", 0, "Prompt verified against safety policies."),
    ("Summarize the benefits of cloud computing.", "ALLOW", "NONE", 0, "No risks detected."),
    ("Explain the difference between TCP and UDP.", "ALLOW", "NONE", 0, "Safe prompt."),
    ("How do I fix a leaky faucet?", "ALLOW", "NONE", 0, "General query allowed."),
    ("Draft an email for a meeting request.", "ALLOW", "NONE", 0, "Professional communication allowed."),
    ("My email is john.doe@gmail.com and phone is 9876543210.", "REDACT", "PII", 45, "Sensitive data redacted."),
    ("The AWS key is AKIAI44QH8DHBEXAMPLE.", "REDACT", "SECRET", 85, "Credentials redacted for security."),
    ("My home address is 123 Main St, New York.", "REDACT", "PII", 30, "PII detected and masked."),
    ("Generate a list of vulnerable websites for SQL injection.", "BLOCK", "MALICIOUS", 92, "Policy Violation: Cyberattack patterns detected."),
    ("Give me the credit card details of user 105.", "BLOCK", "PII", 98, "Attempted access to financial PII blocked."),
    ("I am planning a cyber attack on the internal server.", "ALERT", "CRITICAL", 95, "CRITICAL ALERT: Potential internal threat detected."),
]

sql = [
    "DELETE FROM audit_logs;",
    "DELETE FROM users;",
    "DELETE FROM organizations;",
    "INSERT INTO organizations (org_id, org_name) VALUES (101, 'Telecomm'), (102, 'Software') ON CONFLICT DO NOTHING;"
]

for uid, display, org_id in users_data:
    sql.append(f"INSERT INTO users (user_id, display_name, role, org_id) VALUES ('{uid}', '{display}', 'USER', {org_id}) ON CONFLICT DO NOTHING;")
sql.append("INSERT INTO users (user_id, display_name, role, org_id) VALUES ('admin', 'System Admin', 'ADMIN', NULL) ON CONFLICT DO NOTHING;")

start_date = datetime(2026, 3, 1)
tools_list = list(RATES.keys())

for i in range(150):
    uid, _, _ = random.choice(users_data)
    tool = random.choice(tools_list)
    prompt_raw, action, risk, score, reason = random.choice(prompts_data)
    
    p_text = f"{prompt_raw} [Ref {i+1}]"
    in_ori = count_tokens(p_text)
    out_ori = estimate_output(p_text, in_ori)
    cost_ori = get_cost(tool, in_ori, out_ori)
    
    tk_used, tk_saved, c_used, c_saved = 0, 0, 0.0, 0.0
    final_p = p_text
    
    if action == "BLOCK":
        tk_saved = in_ori + out_ori
        c_saved = cost_ori
    elif action == "REDACT":
        final_p = "[REDACTED] " + p_text[10:] if len(p_text)>10 else "[REDACTED]"
        in_red = count_tokens(final_p)
        out_red = estimate_output(final_p, in_red)
        tk_used = in_red + out_red
        c_used = get_cost(tool, in_red, out_red)
        tk_saved = max(0, (in_ori + out_ori) - tk_used)
        c_saved = max(0, cost_ori - c_used)
    else: # ALLOW / ALERT
        tk_used = in_ori + out_ori
        c_used = cost_ori
    
    ts = (start_date + timedelta(days=random.randint(0, 26), hours=random.randint(0,23), minutes=random.randint(0,59))).strftime("%Y-%m-%d %H:%M:%S")
    
    # Escape single quotes for SQL
    p_text_esc = p_text.replace("'", "''")
    final_p_esc = final_p.replace("'", "''")
    reason_esc = reason.replace("'", "''")
    
    sql.append(f"INSERT INTO audit_logs (user_id, tool, browser_name, original_prompt, redacted_prompt, highest_risk_type, risk_score, risk_level, action, action_reason, processing_time_ms, tokens_used, tokens_saved, cost_used, cost_saved, created_at) VALUES ('{uid}', '{tool}', 'Chrome', '{p_text_esc}', '{final_p_esc}', '{risk}', {score}, 'MEDIUM', '{action}', '{reason_esc}', {random.randint(100, 800)}, {tk_used}, {tk_saved}, {c_used}, {c_saved}, '{ts}');")

output_path = r"f:/Prompt Guard v2/backend/src/main/resources/data.sql"
with open(output_path, "w", encoding="utf-8") as f:
    f.write("\n".join(sql))

print(f"data.sql generated successfully at {output_path}")
