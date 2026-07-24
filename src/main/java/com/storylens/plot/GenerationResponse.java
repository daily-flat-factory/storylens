package com.storylens.plot;

import java.util.List;

public record GenerationResponse(List<Scene> scenes) {

    public record Scene(String stage, String text) {
    }
}
