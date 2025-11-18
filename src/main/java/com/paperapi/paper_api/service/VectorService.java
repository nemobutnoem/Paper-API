package com.paperapi.paper_api.service;

import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VectorService {

    private final EmbeddingClient embeddingClient;

    @Autowired
    public VectorService(EmbeddingClient embeddingClient) {
        this.embeddingClient = embeddingClient;
    }

    public List<Double> getEmbedding(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return embeddingClient.embed(text);
    }
}
