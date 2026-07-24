package com.storylens.plot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

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
        verify(request).user(argThat((String prompt) ->
                prompt.contains("한 장면이라도 5문단 이상이면 응답 전체가 실패 처리됩니다.")
                        && prompt.contains("각 text에 \\n\\n을 1~3개만 넣으세요.")));
        verify(request).options(argThat(
                options -> "gpt-5.6-luna".equals(options.build().getModel())));
    }

    @Test
    void returnsBadGatewayAfterThreeInvalidResponses() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec call = mock(ChatClient.CallResponseSpec.class);
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(request);
        when(request.options(any())).thenReturn(request);
        when(request.user(any(String.class))).thenReturn(request);
        when(request.call()).thenReturn(call);
        when(call.content()).thenReturn("{}");
        NaiveGenerationService service =
                new NaiveGenerationService(builder, JsonMapper.builder().build());

        ResponseStatusException exception =
                assertThrows(ResponseStatusException.class, () -> service.generate("사용자 설정"));

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatusCode());
        assertEquals("LLM이 3회 연속 유효한 Before 생성 JSON을 반환하지 않았습니다.",
                exception.getReason());
        verify(chatClient, times(3)).prompt();
    }

    @Test
    void returnsKoreanErrorBody() {
        StructureGenerationController controller =
                new StructureGenerationController(null, null);

        var response = controller.handle(new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "LLM이 3회 연속 유효한 Before 생성 JSON을 반환하지 않았습니다."));

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertEquals(Map.of(
                "message",
                "LLM이 3회 연속 유효한 Before 생성 JSON을 반환하지 않았습니다."),
                response.getBody());
    }
}
