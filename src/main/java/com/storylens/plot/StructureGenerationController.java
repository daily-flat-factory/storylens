package com.storylens.plot;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class StructureGenerationController {

    private final StructureGenerationService structureGenerationService;
    private final NaiveGenerationService naiveGenerationService;

    public StructureGenerationController(
            StructureGenerationService structureGenerationService,
            NaiveGenerationService naiveGenerationService) {
        this.structureGenerationService = structureGenerationService;
        this.naiveGenerationService = naiveGenerationService;
    }

    @PostMapping("/generate")
    public GenerationResponse generate(@RequestBody GenerationRequest request) {
        if (request == null || request.input() == null || request.input().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "input은 비어 있을 수 없습니다.");
        }
        return structureGenerationService.generate(request.input());
    }

    @PostMapping("/generate-naive")
    public NaiveGenerationResponse generateNaive(@RequestBody GenerationRequest request) {
        if (request == null || request.input() == null || request.input().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "input은 비어 있을 수 없습니다.");
        }
        return naiveGenerationService.generate(request.input());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handle(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode())
                .body(Map.of("message", exception.getReason()));
    }

    public record GenerationRequest(String input) {
    }
}
