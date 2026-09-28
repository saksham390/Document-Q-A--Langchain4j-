package com.example.rag.model;

import java.util.List;

public record AskResponse(String answer, List<Source> sources) {

    public record Source(String document, Integer page) {
    }
}
