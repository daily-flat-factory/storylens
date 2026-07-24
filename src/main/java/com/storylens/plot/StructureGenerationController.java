package com.storylens.plot;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class StructureGenerationController {

    private final StructureGenerationService structureGenerationService;

    public StructureGenerationController(StructureGenerationService structureGenerationService) {
        this.structureGenerationService = structureGenerationService;
    }

    @PostMapping("/generate")
    public GenerationResponse generate(@RequestBody GenerationRequest request) {
        if (request == null || request.input() == null || request.input().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "input은 비어 있을 수 없습니다.");
        }
        return structureGenerationService.generate(request.input());
    }

    public record GenerationRequest(String input) {
    }
}
