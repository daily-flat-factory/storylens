package com.storylens.plot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.storylens.tag.DiagnosisResponse;
import com.storylens.tag.TagDefinitionStore;
import com.storylens.tag.TagDefinitionStore.TagDefinition;

@Component
public class PlotStructureMapper {

    private static final String ALL_STAGES = "전_단계";

    private final TagDefinitionStore tagStore;

    public PlotStructureMapper(TagDefinitionStore tagStore) {
        this.tagStore = tagStore;
    }

    public StructureContext map(DiagnosisResponse diagnosis) {
        Map<String, List<ActiveTag>> stages = new LinkedHashMap<>();
        Map<String, List<ActiveTag>> functionalTypes = new LinkedHashMap<>();
        tagStore.masterPlotStages().forEach(stage -> stages.put(stage, new ArrayList<>()));
        tagStore.functionalTypes().forEach(type -> functionalTypes.put(type, new ArrayList<>()));

        diagnosis.tagResults().stream()
                .filter(DiagnosisResponse.TagResult::active)
                .map(result -> new ActiveTag(tagStore.tag(result.tagId()), result))
                .forEach(activeTag -> {
                    activeTag.definition().masterPlotStages().forEach(stage -> {
                        if (ALL_STAGES.equals(stage)) {
                            stages.values().forEach(tags -> tags.add(activeTag));
                        } else {
                            stages.get(stage).add(activeTag);
                        }
                    });
                    functionalTypes.get(activeTag.definition().functionalType()).add(activeTag);
                });

        return new StructureContext(
                render(stages, this::renderStageTags),
                render(functionalTypes, this::renderFunctionalCondition));
    }

    private Map<String, String> render(
            Map<String, List<ActiveTag>> groups,
            java.util.function.BiFunction<String, List<ActiveTag>, String> renderer) {
        Map<String, String> result = new LinkedHashMap<>();
        groups.forEach((name, tags) -> result.put(name, renderer.apply(name, tags)));
        return Map.copyOf(result);
    }

    private String renderStageTags(String stage, List<ActiveTag> tags) {
        if (tags.isEmpty()) {
            return "해당 단계에 직접 배치된 활성 태그 없음";
        }
        return tags.stream()
                .map(tag -> tag.definition().name() + " — "
                        + tag.definition().psychologicalFunction())
                .collect(Collectors.joining("; "));
    }

    private String renderFunctionalCondition(String type, List<ActiveTag> tags) {
        if (tags.isEmpty()) {
            return "해당 기능유형에 배치된 활성 태그 없음.";
        }
        String action = switch (type) {
            case "핵심목표" -> "이야기의 최종 목표에 반영한다.";
            case "필수사건" -> "반드시 발생하는 사건으로 반영한다.";
            case "지속조건" -> "이야기 전반에 유지한다.";
            case "톤조건" -> "인물의 사고방식과 서술 관점에 유지한다.";
            default -> throw new IllegalArgumentException("정의되지 않은 기능유형입니다: " + type);
        };
        return tags.stream()
                .map(tag -> "입력 근거 "
                        + tag.result().evidence().stream()
                                .distinct()
                                .map(evidence -> "\"" + evidence + "\"")
                                .collect(Collectors.joining(", "))
                        + "에서 감지된 '" + tag.definition().name() + "'의 심리적 기능("
                        + tag.definition().psychologicalFunction() + ")을 " + action)
                .collect(Collectors.joining(" "));
    }

    private record ActiveTag(TagDefinition definition, DiagnosisResponse.TagResult result) {
    }

    public record StructureContext(
            Map<String, String> stageTags,
            Map<String, String> functionalConditions) {
    }
}
