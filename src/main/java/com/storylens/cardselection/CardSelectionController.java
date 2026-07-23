package com.storylens.cardselection;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class CardSelectionController {

    private final CardSelectionService cardSelectionService;

    public CardSelectionController(CardSelectionService cardSelectionService) {
        this.cardSelectionService = cardSelectionService;
    }

    // 세션 29에서 구조 고정 생성과 하나의 통합 엔드포인트로 합칠 임시 테스트용 API다.
    @PostMapping("/select-card")
    public CardSelectionResponse select(@RequestBody SelectionRequest request) {
        if (request == null || request.input() == null || request.input().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "input은 비어 있을 수 없습니다.");
        }
        return cardSelectionService.select(request.input());
    }

    public record SelectionRequest(String input) {
    }
}
