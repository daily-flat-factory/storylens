package com.storylens.cardselection;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class NarrativeCardStore {

    private static final String RESOURCE_PATH = "data/narrative_cards.json";
    private static final String EXCLUDED_STATUS = "검토_후_제외";
    private static final int EXPECTED_CARD_COUNT = 12;
    private static final int MAX_CANDIDATES = 4;

    private final List<JsonNode> cards;

    public NarrativeCardStore(ObjectMapper objectMapper) {
        try {
            String json = new ClassPathResource(RESOURCE_PATH)
                    .getContentAsString(StandardCharsets.UTF_8);
            cards = readCards(objectMapper.readTree(json));
        } catch (IOException | JacksonException exception) {
            throw new IllegalStateException("서사 카드 정의를 로드할 수 없습니다.", exception);
        }
    }

    private List<JsonNode> readCards(JsonNode root) {
        JsonNode cardNodes = root.path("cards");
        if (!cardNodes.isArray() || cardNodes.size() != EXPECTED_CARD_COUNT) {
            throw new IllegalStateException("narrative_cards.json에는 정확히 12개의 카드가 필요합니다.");
        }

        List<JsonNode> loadedCards = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonNode card : cardNodes) {
            String id = card.path("id").asString("");
            if (id.isBlank() || !ids.add(id) || !card.path("connected_tags").isArray()) {
                throw new IllegalStateException("서사 카드 id와 connected_tags가 올바르지 않습니다.");
            }
            loadedCards.add(card);
        }
        return List.copyOf(loadedCards);
    }

    public List<JsonNode> candidates(Set<String> activeTagIds) {
        return cards.stream()
                .filter(card -> !EXCLUDED_STATUS.equals(card.path("status").asString()))
                .filter(card -> matchingTagCount(card, activeTagIds) > 0)
                .sorted((left, right) -> Long.compare(
                        matchingTagCount(right, activeTagIds),
                        matchingTagCount(left, activeTagIds)))
                .limit(MAX_CANDIDATES)
                .toList();
    }

    private long matchingTagCount(JsonNode card, Set<String> activeTagIds) {
        long count = 0;
        for (JsonNode tag : card.path("connected_tags")) {
            if (activeTagIds.contains(tag.asString())) {
                count++;
            }
        }
        return count;
    }
}
