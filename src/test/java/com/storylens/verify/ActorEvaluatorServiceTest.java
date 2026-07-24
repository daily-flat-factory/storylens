package com.storylens.verify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.storylens.plot.GenerationResponse;
import com.storylens.plot.PlotStructureMapper.StructureContext;

import tools.jackson.databind.json.JsonMapper;

class ActorEvaluatorServiceTest {

    @Test
    void stopsAfterThreeInvalidJsonResponses() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec call = mock(ChatClient.CallResponseSpec.class);
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(request);
        when(request.options(any())).thenReturn(request);
        when(request.user(anyString())).thenReturn(request);
        when(request.call()).thenReturn(call);
        when(call.content()).thenReturn("not-json");

        ActorEvaluatorService service =
                new ActorEvaluatorService(builder, JsonMapper.builder().build());
        StructureContext context = new StructureContext(
                Map.of(),
                Map.of(
                        "핵심목표", "목표",
                        "필수사건", "사건",
                        "지속조건", "조건",
                        "톤조건", "톤"));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.evaluate(
                        context,
                        List.of(new GenerationResponse.Scene("상실", "장면"))));

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatusCode());
        verify(call, times(3)).content();
    }

    @Test
    void parsesJsonCodeFence() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec call = mock(ChatClient.CallResponseSpec.class);
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(request);
        when(request.options(any())).thenReturn(request);
        when(request.user(anyString())).thenReturn(request);
        when(request.call()).thenReturn(call);
        when(call.content()).thenReturn("""
                ```json
                {
                  "overall_pass_fail": "PASS",
                  "checklist": [
                    {"item_number":1,"item":"핵심목표","pass_fail":"PASS","evidence":"근거 1"},
                    {"item_number":2,"item":"필수사건","pass_fail":"PASS","evidence":"근거 2"},
                    {"item_number":3,"item":"인과관계","pass_fail":"PASS","evidence":"근거 3"},
                    {"item_number":4,"item":"지속조건","pass_fail":"PASS","evidence":"근거 4"},
                    {"item_number":5,"item":"새 사건","pass_fail":"PASS","evidence":"근거 5"},
                    {"item_number":6,"item":"톤조건","pass_fail":"PASS","evidence":"근거 6"},
                    {"item_number":7,"item":"긴장","pass_fail":"PASS","evidence":"근거 7"}
                  ],
                  "violated_constraint": null,
                  "correction_instruction": null
                }
                ```
                """);

        ActorEvaluatorService service =
                new ActorEvaluatorService(builder, JsonMapper.builder().build());

        VerificationResponse response = service.evaluate(
                context(),
                List.of(new GenerationResponse.Scene("상실", "장면")));

        assertEquals("PASS", response.overallPassFail());
        assertEquals(7, response.checklist().size());
        verify(call).content();
        verify(request).options(argThat(
                options -> "gpt-5.6-terra".equals(options.build().getModel())));
    }

    private StructureContext context() {
        return new StructureContext(
                Map.of(),
                Map.of(
                        "핵심목표", "목표",
                        "필수사건", "사건",
                        "지속조건", "조건",
                        "톤조건", "톤"));
    }
}
