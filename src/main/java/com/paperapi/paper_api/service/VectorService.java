package com.paperapi.paper_api.service;

import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class VectorService {

    // Temporary stub implementation: returns an empty embedding so
    // the project can compile and run without Spring AI dependency.

    public List<Double> getEmbedding(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }
        return Collections.emptyList();
    }
}
