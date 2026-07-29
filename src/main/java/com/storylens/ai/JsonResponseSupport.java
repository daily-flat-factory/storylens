package com.storylens.ai;

import org.springframework.ai.openai.OpenAiChatModel.ResponseFormat;
import org.springframework.ai.openai.OpenAiChatModel.ResponseFormat.Type;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class JsonResponseSupport {

    private static final ResponseFormat JSON_OBJECT = ResponseFormat.builder()
            .type(Type.JSON_OBJECT)
            .build();

    private JsonResponseSupport() {
    }

    public static OpenAiChatOptions.Builder options(String model) {
        OpenAiChatOptions.Builder options = OpenAiChatOptions.builder();
        options.model(model);
        options.responseFormat(JSON_OBJECT);
        return options;
    }

    public static String clean(String content) {
        if (content == null) {
            return "";
        }
        return content.strip()
                .replaceFirst("(?i)^```(?:json)?\\s*", "")
                .replaceFirst("\\s*```$", "")
                .strip();
    }

    /**
     * LLM 호출 자체가 실패한 경우(연결 취소·타임아웃 등)의 공통 예외를 만든다.
     * Spring AI가 이미 내부에서 재시도한 뒤이므로 여기서 또 재시도하지 않고 502로 끝낸다.
     */
    public static ResponseStatusException llmCallFailed(String stage, Throwable cause) {
        return new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                stage + " 단계에서 AI 서버 연결에 실패했습니다. 잠시 후 다시 시도해 주세요.",
                cause);
    }

    public static int paragraphCount(String text) {
        String stripped = text.strip();
        String[] paragraphs = stripped.split("\\R\\s*\\R");
        return paragraphs.length > 1 ? paragraphs.length : stripped.split("\\R").length;
    }
}
