package com.storylens.cardselection;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.storylens.tag.DiagnosisResponse;
import com.storylens.tag.TagDiagnosisService;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class CardSelectionService {

    private static final Logger logger = LoggerFactory.getLogger(CardSelectionService.class);
    private static final String RESOURCE_PATH = "prompts/card-selection.txt";
    private static final String USER_INPUT_PLACEHOLDER = "{{user_input}}";
    private static final String CANDIDATE_CARDS_PLACEHOLDER = "{{candidate_cards}}";
    private static final int MAX_ATTEMPTS = 3;

    private final TagDiagnosisService tagDiagnosisService;
    private final NarrativeCardStore cardStore;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final String promptTemplate;

    public CardSelectionService(
            TagDiagnosisService tagDiagnosisService,
            NarrativeCardStore cardStore,
            ChatClient.Builder chatClientBuilder,
            ObjectMapper objectMapper) {
        this.tagDiagnosisService = tagDiagnosisService;
        this.cardStore = cardStore;
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
        this.promptTemplate = loadPromptTemplate();
    }

    public CardSelectionResponse select(String input) {
        DiagnosisResponse diagnosis = tagDiagnosisService.diagnose(input);
        Set<String> activeTagIds = diagnosis.tagResults().stream()
                .filter(DiagnosisResponse.TagResult::active)
                .map(DiagnosisResponse.TagResult::tagId)
                .collect(java.util.stream.Collectors.toSet());
        List<JsonNode> candidates = cardStore.candidates(activeTagIds);
        Set<String> candidateIds = candidates.stream()
                .map(card -> card.path("id").asString())
                .collect(java.util.stream.Collectors.toSet());
        String prompt = renderPrompt(input, candidates);

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            String content = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
            try {
                CardSelectionResponse response =
                        objectMapper.readValue(content, CardSelectionResponse.class);
                validate(response, candidateIds);
                return response;
            } catch (JacksonException | IllegalArgumentException exception) {
                logger.warn("유효하지 않은 서사 카드 선정 JSON 응답 (시도 {}/{}): {}",
                        attempt, MAX_ATTEMPTS, exception.getMessage());
            }
        }
        throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "LLM이 3회 연속 유효한 서사 카드 선정 JSON을 반환하지 않았습니다.");
    }

    private String loadPromptTemplate() {
        try {
            String template = new ClassPathResource(RESOURCE_PATH)
                    .getContentAsString(StandardCharsets.UTF_8);
            if (!template.contains(USER_INPUT_PLACEHOLDER)
                    || !template.contains(CANDIDATE_CARDS_PLACEHOLDER)) {
                throw new IllegalStateException("서사 카드 선정 프롬프트의 자리표시자가 없습니다.");
            }
            return template;
        } catch (IOException exception) {
            throw new IllegalStateException("서사 카드 선정 프롬프트를 로드할 수 없습니다.", exception);
        }
    }

    private String renderPrompt(String input, List<JsonNode> candidates) {
        try {
            return promptTemplate
                    .replace(USER_INPUT_PLACEHOLDER, input)
                    .replace(CANDIDATE_CARDS_PLACEHOLDER,
                            objectMapper.writeValueAsString(candidates));
        } catch (JacksonException exception) {
            throw new IllegalStateException("후보 서사 카드를 JSON으로 만들 수 없습니다.", exception);
        }
    }

    private void validate(CardSelectionResponse response, Set<String> candidateIds) {
        if (response == null
                || response.selectedCards() == null
                || response.similarityReasons() == null
                || response.abstractPattern() == null
                || response.constraints() == null
                || response.selectedCards().size() > 2
                || !candidateIds.containsAll(response.selectedCards())
                || new HashSet<>(response.selectedCards()).size() != response.selectedCards().size()
                || hasBlank(response.selectedCards())
                || hasBlank(response.similarityReasons())
                || hasBlank(response.abstractPattern())
                || hasBlank(response.constraints())) {
            throw new IllegalArgumentException("서사 카드 선정 결과가 스키마를 충족하지 않습니다.");
        }
    }

    private boolean hasBlank(List<String> values) {
        return values.stream().anyMatch(value -> value == null || value.isBlank());
    }
}
