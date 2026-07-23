package com.storylens.cardselection;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CardSelectionResponse(
        @JsonProperty("selected_cards") List<String> selectedCards,
        @JsonProperty("similarity_reasons") List<String> similarityReasons,
        @JsonProperty("abstract_pattern") List<String> abstractPattern,
        List<String> constraints) {
}
