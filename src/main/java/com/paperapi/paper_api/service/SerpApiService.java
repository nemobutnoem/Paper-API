package com.paperapi.paper_api.service;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import serpapi.GoogleSearch;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SerpApiService {

    @Value("${serpapi.key}")
    private String apiKey;

    public List<Map<String, String>> searchGoogleScholar(String query) {
        List<Map<String, String>> results = new ArrayList<>();
        Map<String, String> parameter = new HashMap<>();

        // Cấu hình tìm kiếm Google Scholar
        parameter.put("engine", "google_scholar");
        parameter.put("q", query);
        parameter.put("hl", "en"); // Ngôn ngữ kết quả
        parameter.put("api_key", apiKey);
        
        // parameter.put("num", "10"); // Số lượng kết quả (mặc định là 10)

        try {
            GoogleSearch search = new GoogleSearch(parameter);
            JsonObject data = search.getJson();
            
            // Kiểm tra xem có kết quả không
            if (data.has("organic_results")) {
                JsonArray organicResults = data.getAsJsonArray("organic_results");

                for (JsonElement result : organicResults) {
                    JsonObject item = result.getAsJsonObject();
                    Map<String, String> paper = new HashMap<>();

                    // 1. Lấy Tiêu đề
                    if (item.has("title")) {
                        paper.put("title", item.get("title").getAsString());
                    }

                    // 2. Lấy Link gốc (đến trang web bài báo)
                    if (item.has("link")) {
                        paper.put("link", item.get("link").getAsString());
                    }

                    // 3. Lấy Snippet (Tóm tắt ngắn)
                    if (item.has("snippet")) {
                        paper.put("snippet", item.get("snippet").getAsString());
                    }

                    // 4. Lấy thông tin xuất bản (Tác giả, Năm, Nguồn)
                    if (item.has("publication_info")) {
                        JsonObject pubInfo = item.getAsJsonObject("publication_info");
                        if (pubInfo.has("summary")) {
                            paper.put("publication_info", pubInfo.get("summary").getAsString());
                        }
                    }
                    
                    // 5. Lấy số lượng trích dẫn (Inline Links)
                    if (item.has("inline_links")) {
                        JsonObject inlineLinks = item.getAsJsonObject("inline_links");
                        if (inlineLinks.has("cited_by")) {
                            JsonObject citedBy = inlineLinks.getAsJsonObject("cited_by");
                            if (citedBy.has("total")) {
                                paper.put("citation_count", String.valueOf(citedBy.get("total").getAsInt()));
                            }
                        }
                    }

                    // 6. QUAN TRỌNG: Lấy Link tải PDF trực tiếp (nếu có)
                    if (item.has("resources")) {
                        JsonArray resources = item.getAsJsonArray("resources");
                        for (JsonElement res : resources) {
                            JsonObject resObj = res.getAsJsonObject();
                            if (resObj.has("file_format") && "PDF".equals(resObj.get("file_format").getAsString())) {
                                paper.put("pdf_link", resObj.get("link").getAsString());
                                break; // Lấy link đầu tiên tìm được
                            }
                        }
                    }
                    
                    // 7. Lấy Paper ID của Google (để tìm bài tương tự sau này)
                    if (item.has("result_id")) {
                        paper.put("google_id", item.get("result_id").getAsString());
                    }

                    results.add(paper);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            // Có thể throw lỗi ra ngoài để Controller bắt
        }
        
        return results;
    }
}