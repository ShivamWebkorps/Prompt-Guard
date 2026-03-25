package com.promptguard.service;

import org.springframework.stereotype.Service;

@Service
public class TokenService {

    /**
     * Estimates the number of tokens in a given text.
     * Heuristic: 1 token per 4 characters.
     */
    public int countTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return (int) Math.ceil(text.length() / 4.0);
    }

    /**
     * Returns an estimated number of tokens for a response based on the prompt
     * content.
     */
    public int getEstimateResponseTokens(String prompt) {
        if (prompt == null || prompt.isBlank())
            return 100;

        String p = prompt.toLowerCase();
        int inputTokens = countTokens(prompt);

        // Dynamic Baseline: responses are often 2x-5x the user's input
        // for typical productive prompts.
        int estimate = Math.max(300, inputTokens * 3);

        // 1. Long-form adjustments
        if (p.contains("essay") || p.contains("article") || p.contains("blog") ||
                p.contains("story") || p.contains("detailed") || p.contains("comprehensive") ||
                p.contains("write a long") || p.contains("elaborate")) {
            estimate = Math.max(1200, estimate * 2);
        }

        // 2. Code generation (tends to be verbose)
        if (p.contains("code") || p.contains("function") || p.contains("implement") ||
                p.contains("script") || p.contains("program") || p.contains("class")) {
            estimate = Math.max(600, estimate + 400);
        }

        // 3. Short-form/Utility (tends to be very compact)
        if (p.contains("summarize") || p.contains("tldr") || p.contains("fix") ||
                p.contains("correct") || p.contains("rephrase") || p.contains("shorten") ||
                p.contains("headline")) {
            estimate = Math.min(estimate, Math.max(150, inputTokens / 2));
        }

        // 4. Specific word count requests
        if (p.contains("100 words"))
            estimate = 150;
        if (p.contains("500 words"))
            estimate = 650;
        if (p.contains("1000 words"))
            estimate = 1300;
        if (p.contains("2000 words"))
            estimate = 2600;

        // Cap to prevent unreasonable estimates
        return Math.min(4000, Math.max(100, estimate));
    }

    /**
     * Calculates the cost saved in USD based on provider pricing.
     * Rates per 1M tokens:
     * - OpenAI (GPT-4o): $2.50 Input / $10.00 Output
     * - Claude (3.5 Sonnet): $3.00 Input / $15.00 Output
     * - Gemini (1.5 Pro): $3.50 Input / $10.50 Output
     */
    public double calculateCost(String tool, int inputTokens, int outputTokens) {
        double inputRate;
        double outputRate;

        String t = (tool != null) ? tool.toLowerCase() : "";

        if (t.contains("openai") || t.contains("gpt") || t.contains("chatgpt")) {
            inputRate = 2.50 / 1_000_000.0;
            outputRate = 10.00 / 1_000_000.0;
        } else if (t.contains("claude") || t.contains("anthropic")) {
            inputRate = 3.00 / 1_000_000.0;
            outputRate = 15.00 / 1_000_000.0;
        } else if (t.contains("gemini") || t.contains("google")) {
            inputRate = 3.50 / 1_000_000.0;
            outputRate = 10.50 / 1_000_000.0;
        } else if (t.contains("grok") || t.contains("xai")) {
            inputRate = 2.00 / 1_000_000.0;
            outputRate = 10.00 / 1_000_000.0;
        } else if (t.contains("deepseek")) {
            inputRate = 0.14 / 1_000_000.0;
            outputRate = 0.28 / 1_000_000.0;
        } else {
            // Default/Fallback
            inputRate = 3.00 / 1_000_000.0;
            outputRate = 12.00 / 1_000_000.0;
        }

        return (inputTokens * inputRate) + (outputTokens * outputRate);
    }
}
