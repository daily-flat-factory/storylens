package com.storylens.plot;

import java.util.List;

public record NaiveGenerationResponse(List<Scene> scenes) {

    public record Scene(String label, String text) {
    }
}
