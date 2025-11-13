package com.paperapi.paper_api.dto;

import lombok.Data;
import java.util.List;

/**
 * DTO cho thông tin bài báo đã được lọc qua metadata
 */
@Data
public class FilteredPaperDTO {
    private Long paperId;
    private String title;
    private String doi;
    private String publicationDate;
    private Long citationCount;
    private String pdfUrl;
    
    // Metadata thông tin
    private String tableDescription; // Mô tả bảng paper từ metadata
    private List<ColumnInfo> importantFields; // Các trường quan trọng
    private List<String> authors;
    private List<String> researchFields;
    private List<String> keywords;
    
    // Thông tin journal/conference
    private String publicationVenue; // Tạp chí hoặc hội nghị
    private Integer publicationYear;
    
    @Data
    public static class ColumnInfo {
        private String columnName;
        private String description;
        private Object value;
    }
}
