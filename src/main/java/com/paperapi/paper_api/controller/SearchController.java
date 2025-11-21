package com.paperapi.paper_api.controller;

import com.paperapi.paper_api.service.SerpApiService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SerpApiService serpApiService;

    public SearchController(SerpApiService serpApiService) {
        this.serpApiService = serpApiService;
    }

    @GetMapping("/scholar")
    public ResponseEntity<List<Map<String, String>>> searchScholar(@RequestParam("query") String query) {
        List<Map<String, String>> results = serpApiService.searchGoogleScholar(query);
        return ResponseEntity.ok(results);
    }
}