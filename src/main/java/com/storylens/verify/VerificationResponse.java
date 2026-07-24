package com.storylens.verify;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record VerificationResponse(
        @JsonProperty("overall_pass_fail") String overallPassFail,
        List<ChecklistItem> checklist,
        @JsonProperty("violated_constraint") String violatedConstraint,
        @JsonProperty("correction_instruction") String correctionInstruction,
        @JsonProperty("regeneration_count") int regenerationCount) {

    public VerificationResponse withRegenerationCount(int count) {
        return new VerificationResponse(
                overallPassFail,
                checklist,
                violatedConstraint,
                correctionInstruction,
                count);
    }

    public boolean passed() {
        return "PASS".equals(overallPassFail);
    }

    public record ChecklistItem(
            @JsonProperty("item_number") int itemNumber,
            String item,
            @JsonProperty("pass_fail") String passFail,
            String evidence) {
    }
}
