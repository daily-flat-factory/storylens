package com.storylens.verify;

import static com.storylens.ai.JsonResponseSupport.clean;
import static com.storylens.ai.JsonResponseSupport.llmCallFailed;
import static com.storylens.ai.JsonResponseSupport.options;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.storylens.plot.GenerationResponse;
import com.storylens.plot.PlotStructureMapper.StructureContext;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class ActorEvaluatorService {

    private static final Logger logger = LoggerFactory.getLogger(ActorEvaluatorService.class);
    private static final String MODEL = "gpt-5.6-terra";
    private static final String PROMPT_PATH = "prompts/actor-evaluator.txt";
    private static final int MAX_ATTEMPTS = 3;
    private static final int CHECKLIST_SIZE = 7;
    private static final Set<String> RESULTS = Set.of("PASS", "FAIL");
    private static final List<String> PLACEHOLDERS = List.of(
            "{{core_goal}}",
            "{{must_events}}",
            "{{persistent_conditions}}",
            "{{tone_conditions}}",
            "{{generated_outline}}");

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final String promptTemplate;

    public ActorEvaluatorService(
            ChatClient.Builder chatClientBuilder,
            ObjectMapper objectMapper) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
        this.promptTemplate = loadPrompt();
        if (PLACEHOLDERS.stream().anyMatch(placeholder -> !promptTemplate.contains(placeholder))) {
            throw new IllegalStateException("Actor-Evaluator 프롬프트의 자리표시자가 없습니다.");
        }
    }

    public VerificationResponse evaluate(
            StructureContext context,
            List<GenerationResponse.Scene> scenes) {
        String prompt = renderPrompt(context, scenes);
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                String content = chatClient.prompt()
                        .options(options(MODEL))
                        .user(prompt)
                        .call()
                        .content();
                logger.debug("Actor-Evaluator LLM 원본 응답 (시도 {}/{}): {}", attempt, MAX_ATTEMPTS, content);
                VerificationResponse response =
                        objectMapper.readValue(clean(content), VerificationResponse.class);
                validate(response);
                return response;
            } catch (JacksonException | IllegalArgumentException exception) {
                logger.warn("유효하지 않은 Actor-Evaluator JSON 응답 (시도 {}/{}): {}",
                        attempt, MAX_ATTEMPTS, exception.getMessage());
            } catch (RuntimeException exception) {
                logger.warn("Actor-Evaluator LLM 호출 실패 (시도 {}/{}): {}",
                        attempt, MAX_ATTEMPTS, exception.toString());
                throw llmCallFailed("자체 검증", exception);
            }
        }
        throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "LLM이 3회 연속 유효한 Actor-Evaluator JSON을 반환하지 않았습니다.");
    }

    private String renderPrompt(
            StructureContext context,
            List<GenerationResponse.Scene> scenes) {
        Map<String, String> values = Map.of(
                "{{core_goal}}", context.functionalConditions().get("핵심목표"),
                "{{must_events}}", context.functionalConditions().get("필수사건"),
                "{{persistent_conditions}}", context.functionalConditions().get("지속조건"),
                "{{tone_conditions}}", context.functionalConditions().get("톤조건"),
                "{{generated_outline}}", writeScenes(scenes));
        String prompt = promptTemplate;
        for (Map.Entry<String, String> value : values.entrySet()) {
            prompt = prompt.replace(value.getKey(), value.getValue());
        }
        return prompt;
    }

    private String writeScenes(List<GenerationResponse.Scene> scenes) {
        try {
            return objectMapper.writeValueAsString(scenes);
        } catch (JacksonException exception) {
            throw new IllegalStateException("생성 아웃라인을 JSON으로 만들 수 없습니다.", exception);
        }
    }

    private void validate(VerificationResponse response) {
        if (response == null || !RESULTS.contains(response.overallPassFail())
                || response.checklist() == null
                || response.checklist().size() != CHECKLIST_SIZE) {
            throw new IllegalArgumentException("검증 결과와 checklist 7개가 필요합니다.");
        }

        boolean allPass = true;
        for (int index = 0; index < CHECKLIST_SIZE; index++) {
            VerificationResponse.ChecklistItem item = response.checklist().get(index);
            if (item == null || item.itemNumber() != index + 1
                    || item.item() == null || item.item().isBlank()
                    || !RESULTS.contains(item.passFail())
                    || item.evidence() == null || item.evidence().isBlank()) {
                throw new IllegalArgumentException("checklist 항목이 스키마를 충족하지 않습니다.");
            }
            allPass &= "PASS".equals(item.passFail());
        }

        if (allPass != response.passed()
                || (response.passed()
                        && (response.violatedConstraint() != null
                                || response.correctionInstruction() != null))
                || (!response.passed()
                        && (response.violatedConstraint() == null
                                || response.violatedConstraint().isBlank()
                                || response.correctionInstruction() == null
                                || response.correctionInstruction().isBlank()))) {
            throw new IllegalArgumentException("전체 판정과 위반·교정 정보가 일치하지 않습니다.");
        }
    }

    private String loadPrompt() {
        try {
            return new ClassPathResource(PROMPT_PATH)
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Actor-Evaluator 프롬프트를 로드할 수 없습니다.", exception);
        }
    }
}
