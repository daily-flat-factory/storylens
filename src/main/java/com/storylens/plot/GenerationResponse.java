package com.storylens.plot;

import java.util.List;

import com.storylens.verify.VerificationResponse;

public record GenerationResponse(
        List<Scene> scenes,
        VerificationResponse verification) {

    public record Scene(String stage, String text) {
    }
}
