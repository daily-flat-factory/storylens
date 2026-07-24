package com.storylens.plot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import tools.jackson.databind.json.JsonMapper;

class NaiveGenerationServiceTest {

    @Test
    void generatesFiveScenesWithTheSameModelAsAfter() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec call = mock(ChatClient.CallResponseSpec.class);
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(request);
        when(request.options(any())).thenReturn(request);
        when(request.user(any(String.class))).thenReturn(request);
        when(request.call()).thenReturn(call);
        when(call.content()).thenReturn("""
                {
                  "scenes": [
                    {"label":"장면1","text":"첫 문단.\\n\\n둘째 문단."},
                    {"label":"장면2","text":"첫 문단.\\n\\n둘째 문단."},
                    {"label":"장면3","text":"첫 문단.\\n\\n둘째 문단."},
                    {"label":"장면4","text":"첫 문단.\\n\\n둘째 문단."},
                    {"label":"장면5","text":"첫 문단.\\n\\n둘째 문단."}
                  ]
                }
                """);
        NaiveGenerationService service =
                new NaiveGenerationService(builder, JsonMapper.builder().build());

        NaiveGenerationResponse response = service.generate("사용자 설정");

        assertEquals(5, response.scenes().size());
        verify(request).user(startsWith(
                "다음 설정을 바탕으로 웹소설 형식의 짧은 이야기를 5개 장면으로 써주세요: 사용자 설정"));
        verify(request).options(argThat(
                options -> "gpt-5.6-luna".equals(options.build().getModel())));
    }
}
