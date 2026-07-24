package com.storylens.ai;

import org.springframework.ai.openai.OpenAiChatModel.ResponseFormat;
import org.springframework.ai.openai.OpenAiChatModel.ResponseFormat.Type;
import org.springframework.ai.openai.OpenAiChatOptions;

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

    public static int paragraphCount(String text) {
        String stripped = text.strip();
        String[] paragraphs = stripped.split("\\R\\s*\\R");
        return paragraphs.length > 1 ? paragraphs.length : stripped.split("\\R").length;
    }
}
