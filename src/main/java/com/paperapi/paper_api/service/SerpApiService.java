package com.paperapi.paper_api.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import serpapi.GoogleSearch;
import serpapi.SerpApiSearchException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SerpApiService {

    @Value("${serpapi.key}")
    private String apiKey;

    // Regex để nhận diện DOI (định dạng chuẩn 10.xxxx/yyyy)
    private static final Pattern DOI_PATTERN = Pattern.compile("10\\.\\d{4,9}/[-._;()/:A-Z0-9]+", Pattern.CASE_INSENSITIVE);

    public List<Map<String, String>> searchGoogleScholar(String query) {
        List<Map<String, String>> results = new ArrayList<>();
        Map<String, String> parameter = new HashMap<>();

        parameter.put("engine", "google_scholar");
        parameter.put("q", query);
        parameter.put("hl", "en");
        parameter.put("api_key", apiKey);
        // parameter.put("num", "20"); // Lấy nhiều kết quả hơn để bù cho những bài bị lọc bỏ

        try {
            GoogleSearch search = new GoogleSearch(parameter);
            JsonObject data = search.getJson();
            
            if (data.has("organic_results")) {
                JsonArray organicResults = data.getAsJsonArray("organic_results");

                for (JsonElement result : organicResults) {
                    JsonObject item = result.getAsJsonObject();
                    
                    // --- BƯỚC KIỂM TRA DOI ---
                    String title = item.has("title") ? item.get("title").getAsString() : "";
                    String snippet = item.has("snippet") ? item.get("snippet").getAsString() : "";
                    String link = item.has("link") ? item.get("link").getAsString() : "";
                    
                    // Gộp nội dung để tìm DOI
                    String fullTextToCheck = title + " " + snippet + " " + link;
                    
                    // Tìm DOI trong văn bản
                    Matcher matcher = DOI_PATTERN.matcher(fullTextToCheck);
                    String foundDoi = null;
                    if (matcher.find()) {
                        foundDoi = matcher.group();
                    }

                    // --- LOGIC LỌC ---
                    // Nếu bạn muốn CHỈ hiện bài có DOI tìm thấy được:
                    // Bỏ comment dòng dưới (Cẩn thận: Có thể mất bài từ ScienceDirect/IEEE)
                    /* if (foundDoi == null) {
                        continue; // Bỏ qua bài này
                    }
                    */ 
                   
                    // Hoặc logic nhẹ nhàng hơn: Chỉ cần có Link (không phải citation) là được
                    if (link.isEmpty()) {
                        continue; // Bỏ qua các trích dẫn không có link đọc
                    }
                    // ------------------

                    Map<String, String> paper = new HashMap<>();
                    paper.put("title", title);
                    paper.put("link", link);
                    paper.put("snippet", snippet);
                    if (foundDoi != null) {
                        paper.put("doi", foundDoi); // Trả về DOI nếu tìm thấy
                    }

                    if (item.has("publication_info")) {
                        JsonObject pubInfo = item.getAsJsonObject("publication_info");
                        if (pubInfo.has("summary")) {
                            paper.put("publication_info", pubInfo.get("summary").getAsString());
                        }
                    }

                    if (item.has("resources")) {
                        JsonArray resources = item.getAsJsonArray("resources");
                        for (JsonElement res : resources) {
                            JsonObject resObj = res.getAsJsonObject();
                            if (resObj.has("file_format") && "PDF".equals(resObj.get("file_format").getAsString())) {
                                paper.put("pdf_link", resObj.get("link").getAsString());
                                break; 
                            }
                        }
                    }
                    
                    if (item.has("inline_links")) {
                        JsonObject inlineLinks = item.getAsJsonObject("inline_links");
                        if (inlineLinks.has("cited_by")) {
                            JsonObject citedBy = inlineLinks.getAsJsonObject("cited_by");
                            if (citedBy.has("total")) {
                                paper.put("citation_count", String.valueOf(citedBy.get("total").getAsInt()));
                            }
                        }
                    }

                    results.add(paper);
                }
            }
        } catch (SerpApiSearchException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return results;
    }
}