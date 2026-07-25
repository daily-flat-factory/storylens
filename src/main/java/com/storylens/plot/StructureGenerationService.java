package com.storylens.plot;

import static com.storylens.ai.JsonResponseSupport.clean;
import static com.storylens.ai.JsonResponseSupport.options;
import static com.storylens.ai.JsonResponseSupport.paragraphCount;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.storylens.cardselection.CardSelectionResponse;
import com.storylens.cardselection.CardSelectionService;
import com.storylens.plot.PlotStructureMapper.StructureContext;
import com.storylens.tag.DiagnosisResponse;
import com.storylens.tag.TagDiagnosisService;
import com.storylens.verify.ActorEvaluatorService;
import com.storylens.verify.VerificationResponse;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class StructureGenerationService {

    private static final Logger logger = LoggerFactory.getLogger(StructureGenerationService.class);
    private static final String MODEL = "gpt-5.6-luna";
    private static final String PROMPT_PATH = "prompts/structure-generation.txt";
    private static final String FEW_SHOT_PATH = "prompts/style-few-shot-example.txt";
    private static final int MAX_ATTEMPTS = 3;
    private static final int MAX_REGENERATIONS = 2;
    private static final List<String> STAGES =
            List.of("상실", "회귀·각성", "목표 설정", "실행", "응징·도달");
    private static final List<String> PLACEHOLDERS = List.of(
            "{{user_input}}",
            "{{selected_narrative_pattern}}",
            "{{core_goal}}",
            "{{must_events}}",
            "{{persistent_conditions}}",
            "{{tone_conditions}}",
            "{{stage_1_tags}}",
            "{{stage_2_tags}}",
            "{{stage_3_tags}}",
            "{{stage_4_tags}}",
            "{{stage_5_tags}}",
            "{{style_few_shot_example}}");

    private final TagDiagnosisService tagDiagnosisService;
    private final CardSelectionService cardSelectionService;
    private final PlotStructureMapper structureMapper;
    private final ActorEvaluatorService actorEvaluatorService;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final String promptTemplate;
    private final String fewShotExample;

    public StructureGenerationService(
            TagDiagnosisService tagDiagnosisService,
            CardSelectionService cardSelectionService,
            PlotStructureMapper structureMapper,
            ActorEvaluatorService actorEvaluatorService,
            ChatClient.Builder chatClientBuilder,
            ObjectMapper objectMapper) {
        this.tagDiagnosisService = tagDiagnosisService;
        this.cardSelectionService = cardSelectionService;
        this.structureMapper = structureMapper;
        this.actorEvaluatorService = actorEvaluatorService;
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
        this.promptTemplate = load(PROMPT_PATH);
        this.fewShotExample = load(FEW_SHOT_PATH);
        if (PLACEHOLDERS.stream().anyMatch(placeholder -> !promptTemplate.contains(placeholder))) {
            throw new IllegalStateException("구조 생성 프롬프트의 자리표시자가 없습니다.");
        }
    }

    public GenerationResponse generate(String input) {
        DiagnosisResponse diagnosis = tagDiagnosisService.diagnose(input);
        CardSelectionResponse cardSelection = cardSelectionService.select(input, diagnosis);
        StructureContext context = structureMapper.map(diagnosis);
        String prompt = renderPrompt(input, cardSelection, context);

        GenerationResponse outline = generateOutline(prompt, 0);
        VerificationResponse verification =
                actorEvaluatorService.evaluate(context, outline.scenes());
        int regenerationCount = 0;
        while (!verification.passed() && regenerationCount < MAX_REGENERATIONS) {
            regenerationCount++;
            logger.info("Actor-Evaluator 검증 실패 → 재생성 사이클 {}/{} 시작 (사유: {})",
                    regenerationCount, MAX_REGENERATIONS, verification.violatedConstraint());
            outline = generateOutline(
                    withCorrection(prompt, verification.correctionInstruction()), regenerationCount);
            verification = actorEvaluatorService.evaluate(context, outline.scenes());
        }
        return new GenerationResponse(
                outline.scenes(),
                verification.withRegenerationCount(regenerationCount),
                diagnosis);
    }

    private GenerationResponse generateOutline(String prompt, int regenerationCycle) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            String content = chatClient.prompt()
                    .options(options(MODEL))
                    .user(prompt)
                    .call()
                    .content();
            logger.debug("구조 생성 LLM 원본 응답 (재생성 {}, 시도 {}/{}): {}",
                    regenerationCycle, attempt, MAX_ATTEMPTS, content);
            try {
                GenerationResponse response =
                        objectMapper.readValue(clean(content), GenerationResponse.class);
                validate(response);
                return response;
            } catch (JacksonException | IllegalArgumentException exception) {
                logger.warn("유효하지 않은 구조 생성 JSON 응답 (재생성 {}, 시도 {}/{}): {}",
                        regenerationCycle, attempt, MAX_ATTEMPTS, exception.getMessage());
            }
        }
        throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "LLM이 3회 연속 유효한 구조 생성 JSON을 반환하지 않았습니다.");
    }

    private String withCorrection(String prompt, String correctionInstruction) {
        return prompt
                + "\n\n[Actor-Evaluator 교정 지시 — 반드시 반영]\n"
                + correctionInstruction;
    }

    private String renderPrompt(
            String input,
            CardSelectionResponse cardSelection,
            StructureContext context) {
        Map<String, String> values = Map.ofEntries(
                Map.entry("{{user_input}}", input),
                Map.entry("{{selected_narrative_pattern}}", renderNarrativePattern(cardSelection)),
                Map.entry("{{core_goal}}", context.functionalConditions().get("핵심목표")),
                Map.entry("{{must_events}}", context.functionalConditions().get("필수사건")),
                Map.entry("{{persistent_conditions}}", context.functionalConditions().get("지속조건")),
                Map.entry("{{tone_conditions}}", context.functionalConditions().get("톤조건")),
                Map.entry("{{stage_1_tags}}", context.stageTags().get("상실")),
                Map.entry("{{stage_2_tags}}", context.stageTags().get("회귀_각성")),
                Map.entry("{{stage_3_tags}}", context.stageTags().get("목표_설정")),
                Map.entry("{{stage_4_tags}}", context.stageTags().get("실행")),
                Map.entry("{{stage_5_tags}}", context.stageTags().get("응징_도달")),
                Map.entry("{{style_few_shot_example}}", fewShotExample));
        String prompt = promptTemplate;
        for (Map.Entry<String, String> value : values.entrySet()) {
            prompt = prompt.replace(value.getKey(), value.getValue());
        }
        return prompt;
    }

    private String renderNarrativePattern(CardSelectionResponse cardSelection) {
        String patterns = cardSelection.abstractPattern().isEmpty()
                ? "- 선정된 추상 패턴 없음"
                : "- " + String.join("\n- ", cardSelection.abstractPattern());
        String constraints = cardSelection.constraints().isEmpty()
                ? "- 추가 사용 제약 없음"
                : "- " + String.join("\n- ", cardSelection.constraints());
        return "추상 패턴:\n" + patterns + "\n사용 제약:\n" + constraints;
    }

    private void validate(GenerationResponse response) {
        if (response == null || response.scenes() == null
                || response.scenes().size() != STAGES.size()) {
            throw new IllegalArgumentException("scenes에는 정확히 5개 장면이 필요합니다.");
        }
        for (int index = 0; index < STAGES.size(); index++) {
            GenerationResponse.Scene scene = response.scenes().get(index);
            if (scene == null || !STAGES.get(index).equals(scene.stage())
                    || scene.text() == null || scene.text().isBlank()) {
                throw new IllegalArgumentException("5개 장면의 단계명과 텍스트가 올바르지 않습니다.");
            }
            int paragraphs = paragraphCount(scene.text());
            int minimum = "실행".equals(scene.stage()) ? 4 : 2;
            int maximum = "실행".equals(scene.stage()) ? 6 : 4;
            if (paragraphs < minimum || paragraphs > maximum) {
                throw new IllegalArgumentException(
                        scene.stage() + " 장면은 " + minimum + "~" + maximum
                                + "문단이어야 합니다. 실제: " + paragraphs);
            }
        }
    }

    private String load(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("구조 생성 리소스를 로드할 수 없습니다: " + path, exception);
        }
    }
}
