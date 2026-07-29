package com.storylens.plot;

import static com.storylens.ai.JsonResponseSupport.clean;
import static com.storylens.ai.JsonResponseSupport.llmCallFailed;
import static com.storylens.ai.JsonResponseSupport.options;
import static com.storylens.ai.JsonResponseSupport.paragraphCount;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class NaiveGenerationService {

    private static final Logger logger = LoggerFactory.getLogger(NaiveGenerationService.class);
    private static final String MODEL = "gpt-5.6-luna";
    private static final int MAX_ATTEMPTS = 3;
    private static final List<String> LABELS =
            List.of("장면1", "장면2", "장면3", "장면4", "장면5");

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public NaiveGenerationService(
            ChatClient.Builder chatClientBuilder,
            ObjectMapper objectMapper) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    public NaiveGenerationResponse generate(String input) {
        String prompt = """
                다음 설정을 바탕으로 웹소설 형식의 짧은 이야기를 5개 장면으로 써주세요: %s

                각 장면은 3문단을 목표로 하되 반드시 2~4문단으로 작성하세요.
                한 장면이라도 5문단 이상이면 응답 전체가 실패 처리됩니다.
                text 안의 문단 사이는 \\n\\n으로만 구분하고, 각 text에 \\n\\n을 1~3개만 넣으세요.
                대사나 시스템 문구도 별도 문단으로 쪼개지 말고 앞뒤 문장과 같은 문단에 포함하세요.
                아래 JSON 형식으로만 응답하세요.
                {"scenes":[
                  {"label":"장면1","text":"..."},
                  {"label":"장면2","text":"..."},
                  {"label":"장면3","text":"..."},
                  {"label":"장면4","text":"..."},
                  {"label":"장면5","text":"..."}
                ]}
                """.formatted(input);

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                String content = chatClient.prompt()
                        .options(options(MODEL))
                        .user(prompt)
                        .call()
                        .content();
                logger.debug("Before 생성 LLM 원본 응답 (시도 {}/{}):\n{}",
                        attempt, MAX_ATTEMPTS, content);
                NaiveGenerationResponse response =
                        objectMapper.readValue(clean(content), NaiveGenerationResponse.class);
                validate(response);
                return response;
            } catch (JacksonException | IllegalArgumentException exception) {
                logger.warn("유효하지 않은 Before 생성 JSON 응답 (시도 {}/{}): {}",
                        attempt, MAX_ATTEMPTS, exception.getMessage());
            } catch (RuntimeException exception) {
                logger.warn("Before 생성 LLM 호출 실패 (시도 {}/{}): {}",
                        attempt, MAX_ATTEMPTS, exception.toString());
                throw llmCallFailed("Before 생성", exception);
            }
        }
        throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "LLM이 3회 연속 유효한 Before 생성 JSON을 반환하지 않았습니다.");
    }

    private void validate(NaiveGenerationResponse response) {
        if (response == null || response.scenes() == null
                || response.scenes().size() != LABELS.size()) {
            throw new IllegalArgumentException("scenes에는 정확히 5개 장면이 필요합니다.");
        }
        for (int index = 0; index < LABELS.size(); index++) {
            NaiveGenerationResponse.Scene scene = response.scenes().get(index);
            if (scene == null || !LABELS.get(index).equals(scene.label())
                    || scene.text() == null || scene.text().isBlank()) {
                throw new IllegalArgumentException("장면1~장면5의 레이블과 텍스트가 올바르지 않습니다.");
            }
            int paragraphs = paragraphCount(scene.text());
            if (paragraphs < 2 || paragraphs > 4) {
                throw new IllegalArgumentException(
                        scene.label() + "은 2~4문단이어야 합니다. 실제: " + paragraphs);
            }
        }
    }
}
