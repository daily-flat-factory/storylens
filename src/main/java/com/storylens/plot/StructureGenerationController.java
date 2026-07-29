package com.storylens.plot;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger logger =
            LoggerFactory.getLogger(StructureGenerationController.class);

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

    /**
     * LLM 호출 취소·타임아웃처럼 예상하지 못한 예외가 그대로 올라오면 500이 되어
     * 프론트에서 원인을 구분할 수 없다. 502로 정규화하고 사유를 로그에 남긴다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handle(Exception exception) {
        logger.error("생성 요청 처리 중 예상하지 못한 오류", exception);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("message", "AI 서버 연결에 실패했습니다. 잠시 후 다시 시도해 주세요."));
    }

    public record GenerationRequest(String input) {
    }
}
