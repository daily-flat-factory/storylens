package com.storylens.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatModel.ResponseFormat.Type;

class JsonResponseSupportTest {

    @Test
    void requestsTheSelectedModelAndJsonObjectResponses() {
        var options = JsonResponseSupport.options("test-model").build();

        assertEquals("test-model", options.getModel());
        assertEquals(
                Type.JSON_OBJECT,
                options.getResponseFormat().getType());
    }

    @Test
    void countsBlankOrSingleLineParagraphSeparators() {
        assertEquals(2, JsonResponseSupport.paragraphCount("첫 문단.\n\n둘째 문단."));
        assertEquals(2, JsonResponseSupport.paragraphCount("첫 문단.\n둘째 문단."));
    }
}
