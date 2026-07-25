package com.storylens.tag;

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

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class TagDiagnosisServiceTest {

    @Test
    void retriesActiveTagsWithoutDisplayStrength() throws Exception {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec call = mock(ChatClient.CallResponseSpec.class);
        ObjectMapper objectMapper = JsonMapper.builder().build();
        TagDefinitionStore tagDefinitionStore = new TagDefinitionStore(objectMapper);
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(request);
        when(request.options(any())).thenReturn(request);
        when(request.system(anyString())).thenReturn(request);
        when(request.user(anyString())).thenReturn(request);
        when(request.call()).thenReturn(call);
        when(call.content()).thenReturn(objectMapper.writeValueAsString(
                new DiagnosisResponse(tagDefinitionStore.tagIds().stream()
                        .map(id -> new DiagnosisResponse.TagResult(
                                id, 80, true, List.of("근거"), null))
                        .toList())));

        TagDiagnosisService service = new TagDiagnosisService(
                builder,
                objectMapper,
                new TagDiagnosisPrompt(tagDefinitionStore),
                tagDefinitionStore);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.diagnose("테스트 입력"));

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatusCode());
        verify(call, times(3)).content();
    }

    @Test
    void parsesJsonCodeFence() throws Exception {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec call = mock(ChatClient.CallResponseSpec.class);
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(request);
        when(request.options(any())).thenReturn(request);
        when(request.system(anyString())).thenReturn(request);
        when(request.user(anyString())).thenReturn(request);
        when(request.call()).thenReturn(call);

        ObjectMapper objectMapper = JsonMapper.builder().build();
        TagDefinitionStore tagDefinitionStore = new TagDefinitionStore(objectMapper);
        DiagnosisResponse expected = new DiagnosisResponse(tagDefinitionStore.tagIds().stream()
                .map(id -> new DiagnosisResponse.TagResult(
                        id, 80, true, List.of("근거"), "강하게 감지"))
                .toList());
        when(call.content()).thenReturn(
                "```json\n" + objectMapper.writeValueAsString(expected) + "\n```");
        TagDiagnosisService service = new TagDiagnosisService(
                builder,
                objectMapper,
                new TagDiagnosisPrompt(tagDefinitionStore),
                tagDefinitionStore);

        DiagnosisResponse response = service.diagnose("테스트 입력");

        assertEquals(9, response.tagResults().size());
        verify(call).content();
        verify(request).system(contains(
                "입력에 \"읽었던\"·\"원작\"·\"해독한\" 중 하나가 있고"));
        verify(request).system(contains(
                "입력에 \"시스템 메시지\"·\"임무\"·\"루트\"·\"미션\" 중 하나가 있고"));
        verify(request).options(argThat(
                options -> "gpt-5-nano".equals(options.build().getModel())));
    }
}
