package com.storylens.tag;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class TagDiagnosisPrompt {

    private static final String RESOURCE_PATH = "prompts/tag-diagnosis.txt";
    private static final String TAG_DEFINITIONS_PLACEHOLDER = "{{tag_definitions}}";

    private final String renderedPrompt;

    public TagDiagnosisPrompt(TagDefinitionStore tagDefinitionStore) {
        try {
            String template = new ClassPathResource(RESOURCE_PATH)
                    .getContentAsString(StandardCharsets.UTF_8);
            if (!template.contains(TAG_DEFINITIONS_PLACEHOLDER)) {
                throw new IllegalStateException("태그 진단 프롬프트에 {{tag_definitions}}가 없습니다.");
            }
            renderedPrompt = template.replace(
                    TAG_DEFINITIONS_PLACEHOLDER,
                    tagDefinitionStore.definitions());
        } catch (IOException exception) {
            throw new IllegalStateException("태그 진단 프롬프트를 로드할 수 없습니다.", exception);
        }
    }

    public String text() {
        return renderedPrompt;
    }
}
