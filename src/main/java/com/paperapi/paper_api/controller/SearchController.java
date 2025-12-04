package com.paperapi.paper_api.controller;

import com.paperapi.paper_api.service.SerpApiService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller xử lý tìm kiếm bài báo
 * Đã được refactor để sử dụng SerpApiService
 */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SerpApiService serpApiService;

    // Constructor Injection (Khuyên dùng thay vì @Autowired trên field)
    public SearchController(SerpApiService serpApiService) {
        this.serpApiService = serpApiService;
    }

    /**
     * Tìm kiếm bài báo trên Google Scholar
     * GET /api/search/scholar?query=machine+learning
     */
    @GetMapping("/scholar")
    public ResponseEntity<?> searchScholar(
            @RequestParam("query") String query,
            @RequestParam(value = "source", required = false) String source // Thêm tham số này
    ) {
        // Nếu source là "science_direct" thì nối chuỗi, không thì thôi
        String finalQuery = query;
        if ("science_direct".equals(source)) {
            finalQuery += " site:sciencedirect.com";
        } else if ("ieee".equals(source)) {
            finalQuery += " site:ieee.org";
        }

        //Gọi service với query đã sửa
        List<Map<String, String>> results = serpApiService.searchGoogleScholar(finalQuery);
        return ResponseEntity.ok(results);
    }
}