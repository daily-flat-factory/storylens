package com.storylens.tag;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class TagDefinitionStore {

    private static final String RESOURCE_PATH = "data/tags.json";
    private static final int EXPECTED_TAG_COUNT = 9;

    private final String definitions;
    private final Set<String> tagIds;

    public TagDefinitionStore(ObjectMapper objectMapper) {
        try {
            definitions = new ClassPathResource(RESOURCE_PATH)
                    .getContentAsString(StandardCharsets.UTF_8);
            tagIds = readTagIds(objectMapper.readTree(definitions));
        } catch (IOException | JacksonException exception) {
            throw new IllegalStateException("태그 정의를 로드할 수 없습니다.", exception);
        }
    }

    private Set<String> readTagIds(JsonNode root) {
        JsonNode tags = root.path("tags");
        if (!tags.isArray() || tags.size() != EXPECTED_TAG_COUNT) {
            throw new IllegalStateException("tags.json에는 정확히 9개의 태그가 필요합니다.");
        }

        Set<String> ids = new LinkedHashSet<>();
        tags.forEach(tag -> ids.add(tag.path("id").asString("")));
        if (ids.size() != EXPECTED_TAG_COUNT || ids.contains("")) {
            throw new IllegalStateException("tags.json의 태그 id는 비어 있지 않고 중복되지 않아야 합니다.");
        }
        return Set.copyOf(ids);
    }

    public String definitions() {
        return definitions;
    }

    public Set<String> tagIds() {
        return tagIds;
    }
}
