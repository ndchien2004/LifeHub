package com.lifehub.domain.ai;

/** One proposed category for a transaction note (FR-AI-07, UC-10). */
public record CategorySuggestion(String categoryId, String categoryName, double confidence) {
}
