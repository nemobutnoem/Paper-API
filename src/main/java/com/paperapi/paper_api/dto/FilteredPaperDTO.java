package com.paperapi.paper_api.dto;

import lombok.Data;
import java.util.List;

/**
 * DTO cho thông tin bài báo với AI-powered intelligent analysis
 */
@Data
public class FilteredPaperDTO {
    private Long paperId;
    private String title;
    private String doi;

    // AI Summary & Analysis
    private IntelligentSummary summary;
    private QualityAssessment qualityAssessment;
    private List<String> recommendations;
    private List<String> warnings;

    // Core Info (minimal)
    private CoreInfo coreInfo;

    @Data
    public static class IntelligentSummary {
        private String paperType; // "Original Research", "Review", "Case Study", "Meta-analysis"
        private String maturityLevel; // "New", "Emerging", "Established", "Classic"
        private String impactLevel; // "High Impact", "Moderate", "Low", "Unknown"
        private String oneLiner; // Tóm tắt 1 câu bằng AI
        private List<String> keyHighlights; // 3-5 điểm nổi bật
    }

    @Data
    public static class QualityAssessment {
        private Integer overallScore; // 0-100
        private String scoreLabel; // "Excellent", "Good", "Fair", "Poor", "Insufficient Data"
        private JournalQuality journalQuality;
        private AuthorCredibility authorCredibility;
        private RecencyScore recencyScore;
        private ImpactMetrics impactMetrics;
    }

    @Data
    public static class JournalQuality {
        private String venueName;
        private String quartile; // "Q1", "Q2", "Q3", "Q4", "Unknown"
        private Float impactFactor;
        private String reputation; // "Top-tier", "Reputable", "Moderate", "Predatory Warning"
    }

    @Data
    public static class AuthorCredibility {
        private Integer totalAuthors;
        private String leadAuthor;
        private Boolean hasInternationalCollaboration;
        private String affiliationType; // "Academic", "Industry", "Mixed"
    }

    @Data
    public static class RecencyScore {
        private Integer yearsSincePublication;
        private String freshnessLabel; // "Brand New", "Recent", "Dated", "Old"
        private Boolean isTrending; // Có nhiều papers tương tự gần đây không
    }

    @Data
    public static class ImpactMetrics {
        private Long citationCount;
        private Float citationsPerYear;
        private String citationTrend; // "Rising", "Stable", "Declining", "Too New"
        private Boolean isHighlyInfuential; // > 100 citations hoặc citations/year > 20
    }

    @Data
    public static class CoreInfo {
        private String publicationDate;
        private Long citationCount;
        private String pdfUrl;
        private List<String> authors;
        private List<String> keywords;
        private String venue;
    }
}
