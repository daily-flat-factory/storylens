package com.storylens.tag;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
    private final Map<String, TagDefinition> tagsById;
    private final List<String> masterPlotStages;
    private final List<String> functionalTypes;

    public TagDefinitionStore(ObjectMapper objectMapper) {
        try {
            definitions = new ClassPathResource(RESOURCE_PATH)
                    .getContentAsString(StandardCharsets.UTF_8);
            JsonNode root = objectMapper.readTree(definitions);
            masterPlotStages = readValues(root.path("master_plot_stages"), 5);
            functionalTypes = readValues(root.path("functional_types"), 4);
            tagsById = readTags(root.path("tags"));
            tagIds = Set.copyOf(tagsById.keySet());
        } catch (IOException | JacksonException exception) {
            throw new IllegalStateException("태그 정의를 로드할 수 없습니다.", exception);
        }
    }

    private List<String> readValues(JsonNode values, int expectedCount) {
        if (!values.isArray() || values.size() != expectedCount) {
            throw new IllegalStateException("tags.json의 단계 또는 기능유형 정의가 올바르지 않습니다.");
        }
        List<String> result = new ArrayList<>();
        values.forEach(value -> result.add(value.asString("")));
        if (result.contains("") || new LinkedHashSet<>(result).size() != expectedCount) {
            throw new IllegalStateException("tags.json의 단계 또는 기능유형은 비어 있지 않고 중복되지 않아야 합니다.");
        }
        return List.copyOf(result);
    }

    private Map<String, TagDefinition> readTags(JsonNode tags) {
        if (!tags.isArray() || tags.size() != EXPECTED_TAG_COUNT) {
            throw new IllegalStateException("tags.json에는 정확히 9개의 태그가 필요합니다.");
        }

        Map<String, TagDefinition> result = new LinkedHashMap<>();
        for (JsonNode tag : tags) {
            String id = tag.path("id").asString("");
            String name = tag.path("name").asString("");
            String psychologicalFunction = tag.path("psychological_function").asString("");
            String functionalType = tag.path("typical_functional_type").asString("");
            List<String> stages = new ArrayList<>();
            tag.path("master_plot_stage").forEach(stage -> stages.add(stage.asString("")));

            if (id.isBlank() || name.isBlank() || psychologicalFunction.isBlank()
                    || result.containsKey(id) || stages.isEmpty()
                    || stages.stream().anyMatch(stage -> !masterPlotStages.contains(stage)
                            && !"전_단계".equals(stage))
                    || !functionalTypes.contains(functionalType)) {
                throw new IllegalStateException("tags.json의 태그 구조 필드가 올바르지 않습니다.");
            }
            result.put(id, new TagDefinition(
                    id, name, psychologicalFunction, List.copyOf(stages), functionalType));
        }
        return Map.copyOf(result);
    }

    public String definitions() {
        return definitions;
    }

    public Set<String> tagIds() {
        return tagIds;
    }

    public TagDefinition tag(String id) {
        TagDefinition tag = tagsById.get(id);
        if (tag == null) {
            throw new IllegalArgumentException("정의되지 않은 태그 id입니다: " + id);
        }
        return tag;
    }

    public List<String> masterPlotStages() {
        return masterPlotStages;
    }

    public List<String> functionalTypes() {
        return functionalTypes;
    }

    public record TagDefinition(
            String id,
            String name,
            String psychologicalFunction,
            List<String> masterPlotStages,
            String functionalType) {
    }
}
