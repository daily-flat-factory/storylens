package com.storylens.plot;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.storylens.tag.DiagnosisResponse;
import com.storylens.tag.TagDefinitionStore;

import tools.jackson.databind.json.JsonMapper;

class PlotStructureMapperTest {

    @Test
    void mapsEveryActiveTagByStageAndFunctionalType() {
        PlotStructureMapper mapper = new PlotStructureMapper(
                new TagDefinitionStore(JsonMapper.builder().build()));
        DiagnosisResponse diagnosis = new DiagnosisResponse(List.of(
                active("revenge_retribution", "후계자에 올라 배신자를 응징한다"),
                active("regret_retry", "죽은 뒤 막내아들로 다시 태어났다"),
                active("informational_advantage_twist", "미래의 기억이 남아 있다"),
                active("identity_ambiguity", "성인의 기억을 가진 갓난아기")));

        PlotStructureMapper.StructureContext context = mapper.map(diagnosis);

        assertTrue(context.stageTags().get("상실").contains("복수/응징"));
        assertTrue(context.stageTags().get("응징_도달").contains("복수/응징"));
        assertTrue(context.stageTags().values().stream()
                .allMatch(tags -> tags.contains("정체성 모호성")));
        assertTrue(context.functionalConditions().get("핵심목표")
                .contains("후계자에 올라 배신자를 응징한다"));
        assertTrue(context.functionalConditions().get("핵심목표")
                .contains("부당한 피해에 대한 정의 회복 욕구"));
        assertTrue(context.functionalConditions().get("필수사건")
                .contains("죽은 뒤 막내아들로 다시 태어났다"));
        assertTrue(context.functionalConditions().get("지속조건")
                .contains("미래의 기억이 남아 있다"));
        assertTrue(context.functionalConditions().get("톤조건")
                .contains("어느 쪽이 진짜 나인가"));
    }

    private DiagnosisResponse.TagResult active(String id, String evidence) {
        return new DiagnosisResponse.TagResult(
                id, 80, true, List.of(evidence), "강하게 감지");
    }
}
