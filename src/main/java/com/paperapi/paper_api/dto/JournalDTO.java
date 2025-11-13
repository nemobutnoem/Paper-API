package com.paperapi.paper_api.dto;

import lombok.Data;

@Data
public class JournalDTO {
    private String title;
    private String issn;
    private Double impactFactor;
    private PublisherDTO publisher;
}
