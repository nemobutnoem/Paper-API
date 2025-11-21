package com.paperapi.paper_api.dto;

import java.util.List;

import lombok.Data;

// GoogleSearchResponseDTO.java
@Data
public class GoogleSearchResponseDTO {
    private List<Item> items;

    @Data
    public static class Item {
        private String title;
        private String url; // URL
        private String abstractText; // Tóm tắt ngắn
        private String doi; // Domain (vd: sciencedirect.com)
    }
}