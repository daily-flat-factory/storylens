package com.storylens.plot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.storylens.cardselection.CardSelectionResponse;
import com.storylens.cardselection.CardSelectionService;
import com.storylens.tag.DiagnosisResponse;
import com.storylens.tag.TagDefinitionStore;
import com.storylens.tag.TagDiagnosisService;
import com.storylens.verify.ActorEvaluatorService;
import com.storylens.verify.VerificationResponse;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class StructureGenerationServiceTest {

    private static final String VALID_OUTLINE = """
            {
              "scenes": [
                {"stage":"상실","text":"첫 문단.\\n\\n둘째 문단."},
                {"stage":"회귀·각성","text":"첫 문단.\\n\\n둘째 문단."},
                {"stage":"목표 설정","text":"첫 문단.\\n\\n둘째 문단."},
                {"stage":"실행","text":"첫 문단.\\n\\n둘째 문단."},
                {"stage":"응징·도달","text":"첫 문단.\\n\\n둘째 문단."}
              ]
            }
            """;

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

        TagDiagnosisService diagnosisService = mock(TagDiagnosisService.class);
        CardSelectionService cardSelectionService = mock(CardSelectionService.class);
        ActorEvaluatorService actorEvaluatorService = mock(ActorEvaluatorService.class);
        DiagnosisResponse diagnosis = new DiagnosisResponse(List.of(
                new DiagnosisResponse.TagResult(
                        "revenge_retribution",
                        80,
                        true,
                        List.of("배신자를 응징한다"),
                        "강하게 감지")));
        when(diagnosisService.diagnose(anyString())).thenReturn(diagnosis);
        when(cardSelectionService.select(anyString(), any()))
                .thenReturn(new CardSelectionResponse(
                        List.of(), List.of(), List.of(), List.of()));

        ObjectMapper objectMapper = JsonMapper.builder().build();
        StructureGenerationService service = new StructureGenerationService(
                diagnosisService,
                cardSelectionService,
                new PlotStructureMapper(new TagDefinitionStore(objectMapper)),
                actorEvaluatorService,
                builder,
                objectMapper);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.generate("테스트 입력"));

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatusCode());
        verify(call, times(3)).content();
    }

    @Test
    void parsesJsonCodeFenceAndStopsAfterTwoFailedRegenerations() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec call = mock(ChatClient.CallResponseSpec.class);
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(request);
        when(request.options(any())).thenReturn(request);
        when(request.user(anyString())).thenReturn(request);
        when(request.call()).thenReturn(call);
        when(call.content()).thenReturn("```json\n" + VALID_OUTLINE + "\n```");

        TagDiagnosisService diagnosisService = mock(TagDiagnosisService.class);
        CardSelectionService cardSelectionService = mock(CardSelectionService.class);
        ActorEvaluatorService actorEvaluatorService = mock(ActorEvaluatorService.class);
        DiagnosisResponse diagnosis = new DiagnosisResponse(List.of(
                new DiagnosisResponse.TagResult(
                        "revenge_retribution",
                        80,
                        true,
                        List.of("배신자를 응징한다"),
                        "강하게 감지")));
        when(diagnosisService.diagnose(anyString())).thenReturn(diagnosis);
        when(cardSelectionService.select(anyString(), any()))
                .thenReturn(new CardSelectionResponse(
                        List.of(), List.of(), List.of(), List.of()));
        VerificationResponse failedVerification = new VerificationResponse(
                "FAIL",
                IntStream.rangeClosed(1, 7)
                        .mapToObj(number -> new VerificationResponse.ChecklistItem(
                                number,
                                "항목 " + number,
                                number == 1 ? "FAIL" : "PASS",
                                "근거"))
                        .toList(),
                "1번 핵심목표",
                "핵심목표를 장면에 명시한다.",
                0);
        when(actorEvaluatorService.evaluate(any(), any())).thenReturn(failedVerification);

        ObjectMapper objectMapper = JsonMapper.builder().build();
        StructureGenerationService service = new StructureGenerationService(
                diagnosisService,
                cardSelectionService,
                new PlotStructureMapper(new TagDefinitionStore(objectMapper)),
                actorEvaluatorService,
                builder,
                objectMapper);

        GenerationResponse response = service.generate("테스트 입력");

        assertEquals(diagnosis, response.diagnosis());
        assertEquals("FAIL", response.verification().overallPassFail());
        assertEquals(2, response.verification().regenerationCount());
        verify(call, times(3)).content();
        verify(actorEvaluatorService, times(3)).evaluate(any(), any());
        verify(request, times(3)).user(contains("연속된 하나의 세계가 아니라 평행한 별개의 실재"));
        verify(request, times(2)).user(contains("[Actor-Evaluator 교정 지시"));
        verify(request, times(3)).options(argThat(
                options -> "gpt-5.6-luna".equals(options.build().getModel())));
    }
}
