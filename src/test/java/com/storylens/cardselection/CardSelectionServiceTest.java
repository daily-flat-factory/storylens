package com.storylens.cardselection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.storylens.tag.DiagnosisResponse;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class CardSelectionServiceTest {

    @Test
    void keepsFourCandidatesWithTheMostMatchingTags() {
        NarrativeCardStore store = new NarrativeCardStore(JsonMapper.builder().build());
        JsonNode monteCristo = store.candidates(Set.of("revenge_retribution")).getFirst();
        monteCristo.withArray("connected_tags").add("informational_advantage_twist");

        List<JsonNode> candidates = store.candidates(Set.of(
                "revenge_retribution",
                "regret_retry",
                "informational_advantage_twist",
                "social_status_reversal",
                "metacognitive_dual_perspective"));

        assertEquals(4, candidates.size());
        assertEquals("count_of_monte_cristo", candidates.getFirst().path("id").asString());
        assertFalse(candidates.stream()
                .anyMatch(card -> "wuthering_heights".equals(card.path("id").asString())));
    }

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

        DiagnosisResponse diagnosis = new DiagnosisResponse(List.of(
                new DiagnosisResponse.TagResult(
                        "revenge_retribution",
                        80,
                        true,
                        List.of("배신"),
                        "강하게 감지")));

        ObjectMapper objectMapper = JsonMapper.builder().build();
        CardSelectionService service = new CardSelectionService(
                new NarrativeCardStore(objectMapper),
                builder,
                objectMapper);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.select("테스트 입력", diagnosis));

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
                  "selected_cards": ["count_of_monte_cristo"],
                  "similarity_reasons": ["상실과 응징 구조"],
                  "abstract_pattern": ["부당한 상실", "계획적 응징"],
                  "constraints": ["원작 고유명사를 사용하지 않는다"]
                }
                ```
                """);

        DiagnosisResponse diagnosis = new DiagnosisResponse(List.of(
                new DiagnosisResponse.TagResult(
                        "revenge_retribution",
                        80,
                        true,
                        List.of("배신"),
                        "강하게 감지")));
        ObjectMapper objectMapper = JsonMapper.builder().build();
        CardSelectionService service = new CardSelectionService(
                new NarrativeCardStore(objectMapper),
                builder,
                objectMapper);

        CardSelectionResponse response = service.select("테스트 입력", diagnosis);

        assertEquals(List.of("count_of_monte_cristo"), response.selectedCards());
        verify(call).content();
        verify(request).options(argThat(
                options -> "gpt-5-mini".equals(options.build().getModel())));
    }
}
