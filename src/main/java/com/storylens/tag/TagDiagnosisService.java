package com.storylens.tag;

import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class TagDiagnosisService {

    private static final Logger logger = LoggerFactory.getLogger(TagDiagnosisService.class);
    private static final int MAX_ATTEMPTS = 3;
    private static final Set<String> DISPLAY_STRENGTHS =
            Set.of("강하게 감지", "중간 감지", "약하게 감지");

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final TagDiagnosisPrompt prompt;
    private final Set<String> expectedTagIds;

    public TagDiagnosisService(
            ChatClient.Builder chatClientBuilder,
            ObjectMapper objectMapper,
            TagDiagnosisPrompt prompt,
            TagDefinitionStore tagDefinitionStore) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
        this.prompt = prompt;
        this.expectedTagIds = tagDefinitionStore.tagIds();
    }

    public DiagnosisResponse diagnose(String input) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            String content = chatClient.prompt()
                    .system(prompt.text())
                    .user(input)
                    .call()
                    .content();
            try {
                DiagnosisResponse response =
                        objectMapper.readValue(content, DiagnosisResponse.class);
                validate(response);
                return response;
            } catch (JacksonException | IllegalArgumentException exception) {
                logger.warn("유효하지 않은 태그 진단 JSON 응답 (시도 {}/{}): {}",
                        attempt, MAX_ATTEMPTS, exception.getMessage());
            }
        }
        throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "LLM이 3회 연속 유효한 태그 진단 JSON을 반환하지 않았습니다.");
    }

    private void validate(DiagnosisResponse response) {
        if (response == null || response.tagResults() == null
                || response.tagResults().size() != expectedTagIds.size()) {
            throw new IllegalArgumentException("tag_results에는 9개 태그 결과가 필요합니다.");
        }

        Set<String> actualTagIds = response.tagResults().stream()
                .map(DiagnosisResponse.TagResult::tagId)
                .collect(Collectors.toSet());
        if (!actualTagIds.equals(expectedTagIds)) {
            throw new IllegalArgumentException("tag_results의 태그 id가 tags.json과 일치하지 않습니다.");
        }

        for (DiagnosisResponse.TagResult result : response.tagResults()) {
            if (result.score() < 0 || result.score() > 100
                    || result.active() != (result.score() >= 40)
                    || result.evidence() == null
                    || result.evidence().isEmpty()
                    || result.evidence().stream()
                            .anyMatch(evidence -> evidence == null || evidence.isBlank())
                    || (result.active() && !DISPLAY_STRENGTHS.contains(result.displayStrength()))) {
                throw new IllegalArgumentException("태그 결과가 진단 스키마를 충족하지 않습니다.");
            }
        }
    }
}
