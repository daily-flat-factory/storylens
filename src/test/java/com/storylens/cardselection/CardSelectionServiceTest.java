package com.storylens.cardselection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
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
import com.storylens.tag.TagDiagnosisService;

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
        when(request.user(anyString())).thenReturn(request);
        when(request.call()).thenReturn(call);
        when(call.content()).thenReturn("not-json");

        TagDiagnosisService tagDiagnosisService = mock(TagDiagnosisService.class);
        when(tagDiagnosisService.diagnose(anyString())).thenReturn(new DiagnosisResponse(List.of(
                new DiagnosisResponse.TagResult(
                        "revenge_retribution",
                        80,
                        true,
                        List.of("배신"),
                        "강하게 감지"))));

        ObjectMapper objectMapper = JsonMapper.builder().build();
        CardSelectionService service = new CardSelectionService(
                tagDiagnosisService,
                new NarrativeCardStore(objectMapper),
                builder,
                objectMapper);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.select("테스트 입력"));

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatusCode());
        verify(call, times(3)).content();
    }
}
