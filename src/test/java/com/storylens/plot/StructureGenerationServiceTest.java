package com.storylens.plot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.storylens.cardselection.CardSelectionResponse;
import com.storylens.cardselection.CardSelectionService;
import com.storylens.tag.DiagnosisResponse;
import com.storylens.tag.TagDefinitionStore;
import com.storylens.tag.TagDiagnosisService;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class StructureGenerationServiceTest {

    @Test
    void stopsAfterThreeInvalidJsonResponses() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec call = mock(ChatClient.CallResponseSpec.class);
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(request);
        when(request.user(anyString())).thenReturn(request);
        when(request.call()).thenReturn(call);
        when(call.content()).thenReturn("not-json");

        TagDiagnosisService diagnosisService = mock(TagDiagnosisService.class);
        CardSelectionService cardSelectionService = mock(CardSelectionService.class);
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
                builder,
                objectMapper);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.generate("테스트 입력"));

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatusCode());
        verify(call, times(3)).content();
    }
}
