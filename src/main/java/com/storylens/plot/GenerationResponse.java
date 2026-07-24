package com.storylens.plot;

import java.util.List;

import com.storylens.tag.DiagnosisResponse;
import com.storylens.verify.VerificationResponse;

public record GenerationResponse(
        List<Scene> scenes,
        VerificationResponse verification,
        DiagnosisResponse diagnosis) {

    public record Scene(String stage, String text) {
    }
}
