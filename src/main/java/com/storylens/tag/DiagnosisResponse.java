package com.storylens.tag;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DiagnosisResponse(
        @JsonProperty("tag_results") List<TagResult> tagResults) {

    public record TagResult(
            @JsonProperty("tag_id") String tagId,
            int score,
            @JsonProperty("is_active") boolean active,
            List<String> evidence,
            @JsonProperty("display_strength") String displayStrength) {
    }
}
