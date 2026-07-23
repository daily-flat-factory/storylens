package com.storylens.tag;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class TagDiagnosisController {

    private final TagDiagnosisService tagDiagnosisService;

    public TagDiagnosisController(TagDiagnosisService tagDiagnosisService) {
        this.tagDiagnosisService = tagDiagnosisService;
    }

    @PostMapping("/diagnose")
    public DiagnosisResponse diagnose(@RequestBody DiagnosisRequest request) {
        if (request.input() == null || request.input().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "input은 비어 있을 수 없습니다.");
        }
        return tagDiagnosisService.diagnose(request.input());
    }

    public record DiagnosisRequest(String input) {
    }
}
