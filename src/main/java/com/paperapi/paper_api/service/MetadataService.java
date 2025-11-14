package com.paperapi.paper_api.service;

import com.paperapi.paper_api.dto.FilteredPaperDTO;
import com.paperapi.paper_api.entity.*;
import com.paperapi.paper_api.repository.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MetadataService {

    private final JdbcTemplate jdbcTemplate;
    private final PaperRepository paperRepository;

    public MetadataService(JdbcTemplate jdbcTemplate, PaperRepository paperRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.paperRepository = paperRepository;
    }

    /**
     * AI-Powered Intelligent Filtering & Analysis
     * Phân tích paper và trả về insights thông minh
     */
    @Transactional(readOnly = true)
    public FilteredPaperDTO filterPaperInfo(Long paperId) {
        // 1. Lấy paper từ database
        Paper paper = paperRepository.findById(paperId)
                .orElseThrow(() -> new RuntimeException("Paper not found with ID: " + paperId));

        FilteredPaperDTO filtered = new FilteredPaperDTO();

        // 2. Basic Info
        filtered.setPaperId(paper.getPaperId());
        filtered.setTitle(paper.getTitle());
        filtered.setDoi(paper.getDoi());

        // 3. AI Intelligent Summary
        filtered.setSummary(generateIntelligentSummary(paper));

        // 4. Quality Assessment (AI-powered)
        filtered.setQualityAssessment(assessQuality(paper));

        // 5. Smart Recommendations
        filtered.setRecommendations(generateRecommendations(paper));

        // 6. Warnings (nếu có)
        filtered.setWarnings(generateWarnings(paper));

        // 7. Core Info (minimal, chỉ khi cần)
        filtered.setCoreInfo(extractCoreInfo(paper));

        filtered.setCoreInfo(extractCoreInfo(paper));

        return filtered;
    }

    /**
     * Generate AI-powered intelligent summary
     */
    private FilteredPaperDTO.IntelligentSummary generateIntelligentSummary(Paper paper) {
        FilteredPaperDTO.IntelligentSummary summary = new FilteredPaperDTO.IntelligentSummary();

        // Xác định paper type dựa vào keywords và title
        summary.setPaperType(detectPaperType(paper));

        // Đánh giá maturity level dựa vào publication date và citations
        summary.setMaturityLevel(assessMaturityLevel(paper));

        // Impact level
        summary.setImpactLevel(assessImpactLevel(paper));

        // One-liner summary
        summary.setOneLiner(generateOneLiner(paper));

        // Key highlights
        summary.setKeyHighlights(extractKeyHighlights(paper));

        return summary;
    }

    /**
     * Comprehensive quality assessment
     */
    private FilteredPaperDTO.QualityAssessment assessQuality(Paper paper) {
        FilteredPaperDTO.QualityAssessment assessment = new FilteredPaperDTO.QualityAssessment();

        // Journal/Conference quality
        assessment.setJournalQuality(assessJournalQuality(paper));

        // Author credibility
        assessment.setAuthorCredibility(assessAuthorCredibility(paper));

        // Recency score
        assessment.setRecencyScore(assessRecency(paper));

        // Impact metrics
        assessment.setImpactMetrics(calculateImpactMetrics(paper));

        // Overall score (0-100)
        int score = calculateOverallScore(assessment);
        assessment.setOverallScore(score);
        assessment.setScoreLabel(getScoreLabel(score));

        return assessment;
    }

    /**
     * Generate smart recommendations
     */
    private List<String> generateRecommendations(Paper paper) {
        List<String> recommendations = new ArrayList<>();

        Long citations = paper.getCitationCount() != null ? paper.getCitationCount() : 0;
        int yearsSince = getYearsSincePublication(paper);

        if (yearsSince == 0) {
            recommendations
                    .add("📅 Paper mới xuất bản - Chưa có đánh giá từ cộng đồng. Hãy đọc abstract kỹ để tự đánh giá.");
        }

        if (paper.getPdfUrl() != null && !paper.getPdfUrl().isEmpty()) {
            recommendations.add("📄 Open access - Đọc full-text miễn phí để hiểu sâu hơn.");
        }

        if (citations == 0 && yearsSince > 1) {
            recommendations.add("⏳ Chưa được trích dẫn sau " + yearsSince
                    + " năm - Cân nhắc tìm papers tương tự có impact cao hơn.");
        }

        if (citations > 50) {
            recommendations.add("⭐ Highly cited paper - Đáng tin cậy, nên đọc và cite.");
        }

        if (paper.getPaperAuthors() != null && paper.getPaperAuthors().size() > 5) {
            recommendations.add("🤝 Large collaboration - Nghiên cứu liên ngành hoặc multi-site study.");
        }

        return recommendations;
    }

    /**
     * Generate warnings
     */
    private List<String> generateWarnings(Paper paper) {
        List<String> warnings = new ArrayList<>();

        Long citations = paper.getCitationCount() != null ? paper.getCitationCount() : 0;
        int yearsSince = getYearsSincePublication(paper);

        if (yearsSince == 0) {
            warnings.add("⚠️ Paper mới - Chưa có peer validation từ cộng đồng");
        }

        if (paper.getPdfUrl() == null || paper.getPdfUrl().isEmpty()) {
            warnings.add("🔒 Không có PDF - Cần truy cập qua thư viện hoặc mua");
        }

        if (citations == 0 && yearsSince >= 2) {
            warnings.add("⚠️ Không có citations sau " + yearsSince + " năm - Có thể chất lượng thấp hoặc niche topic");
        }

        return warnings;
    }

    /**
     * Extract core info
     */
    private FilteredPaperDTO.CoreInfo extractCoreInfo(Paper paper) {
        FilteredPaperDTO.CoreInfo core = new FilteredPaperDTO.CoreInfo();

        core.setPublicationDate(paper.getPublicationDate());
        core.setCitationCount(paper.getCitationCount());
        core.setPdfUrl(paper.getPdfUrl());

        // Authors (top 3)
        if (paper.getPaperAuthors() != null) {
            List<String> authors = paper.getPaperAuthors().stream()
                    .sorted((a, b) -> Integer.compare(a.getAuthorOrder(), b.getAuthorOrder()))
                    .limit(3)
                    .map(pa -> pa.getAuthor().getFirstname() + " " + pa.getAuthor().getLastname())
                    .collect(Collectors.toList());
            if (paper.getPaperAuthors().size() > 3) {
                authors.add("et al.");
            }
            core.setAuthors(authors);
        }

        // Keywords
        if (paper.getKeywords() != null) {
            core.setKeywords(
                    List.of(paper.getKeywords().split(",\\s*")).stream().limit(5).collect(Collectors.toList()));
        }

        // Venue
        if (paper.getVolume() != null && paper.getVolume().getJournal() != null) {
            core.setVenue(paper.getVolume().getJournal().getTitle());
        } else if (paper.getConference() != null) {
            core.setVenue(paper.getConference().getName());
        }

        return core;
    }

    // ===== HELPER METHODS =====

    private String detectPaperType(Paper paper) {
        String title = paper.getTitle() != null ? paper.getTitle().toLowerCase() : "";
        String keywords = paper.getKeywords() != null ? paper.getKeywords().toLowerCase() : "";

        if (title.contains("review") || title.contains("survey") || keywords.contains("review")) {
            return "Review Paper";
        } else if (title.contains("case study") || keywords.contains("case study")) {
            return "Case Study";
        } else if (title.contains("meta-analysis")) {
            return "Meta-analysis";
        }
        return "Original Research";
    }

    private String assessMaturityLevel(Paper paper) {
        int years = getYearsSincePublication(paper);
        Long citations = paper.getCitationCount() != null ? paper.getCitationCount() : 0;

        if (years == 0)
            return "Brand New (2025)";
        if (years <= 2 && citations < 20)
            return "Emerging";
        if (years <= 5 && citations > 50)
            return "Established";
        if (years > 10 && citations > 100)
            return "Classic";
        return "Developing";
    }

    private String assessImpactLevel(Paper paper) {
        Long citations = paper.getCitationCount() != null ? paper.getCitationCount() : 0;
        int years = getYearsSincePublication(paper);

        if (years == 0)
            return "Unknown (Too New)";

        float citationsPerYear = years > 0 ? citations.floatValue() / years : 0;

        if (citations > 100 || citationsPerYear > 20)
            return "High Impact";
        if (citations > 20 || citationsPerYear > 5)
            return "Moderate Impact";
        if (citations > 5)
            return "Low Impact";
        return "Minimal/No Impact";
    }

    private String generateOneLiner(Paper paper) {
        String type = detectPaperType(paper);
        String venue = "";
        if (paper.getVolume() != null && paper.getVolume().getJournal() != null) {
            venue = paper.getVolume().getJournal().getTitle();
        } else if (paper.getConference() != null) {
            venue = paper.getConference().getName();
        }

        int year = getPublicationYear(paper);
        Long citations = paper.getCitationCount() != null ? paper.getCitationCount() : 0;

        return String.format("%s published in %s (%d) with %d citations", type, venue, year, citations);
    }

    private List<String> extractKeyHighlights(Paper paper) {
        List<String> highlights = new ArrayList<>();

        // Highlight 1: Publication status
        int year = getPublicationYear(paper);
        if (year == 2025) {
            highlights.add("Mới xuất bản năm 2025");
        } else if (year >= 2023) {
            highlights.add("Nghiên cứu gần đây (2023-2024)");
        }

        // Highlight 2: Access
        if (paper.getPdfUrl() != null && !paper.getPdfUrl().isEmpty()) {
            highlights.add("Open access - PDF miễn phí");
        }

        // Highlight 3: Collaboration
        if (paper.getPaperAuthors() != null) {
            int authorCount = paper.getPaperAuthors().size();
            if (authorCount > 5) {
                highlights.add(authorCount + " tác giả - Nghiên cứu hợp tác lớn");
            }
        }

        // Highlight 4: Citations
        Long citations = paper.getCitationCount() != null ? paper.getCitationCount() : 0;
        if (citations > 100) {
            highlights.add("Highly cited (" + citations + " citations)");
        }

        // Highlight 5: Fields
        if (paper.getPaperFields() != null && paper.getPaperFields().size() > 2) {
            highlights.add("Interdisciplinary research");
        }

        return highlights.isEmpty() ? List.of("Standard research paper") : highlights;
    }

    private FilteredPaperDTO.JournalQuality assessJournalQuality(Paper paper) {
        FilteredPaperDTO.JournalQuality jq = new FilteredPaperDTO.JournalQuality();

        if (paper.getVolume() != null && paper.getVolume().getJournal() != null) {
            var journal = paper.getVolume().getJournal();
            jq.setVenueName(journal.getTitle());
            jq.setImpactFactor(journal.getImpactFactor() != null ? journal.getImpactFactor().floatValue() : null);

            // Quartile estimation based on impact factor
            if (journal.getImpactFactor() != null) {
                float if_ = journal.getImpactFactor().floatValue();
                if (if_ > 10)
                    jq.setQuartile("Q1");
                else if (if_ > 5)
                    jq.setQuartile("Q1-Q2");
                else if (if_ > 2)
                    jq.setQuartile("Q2-Q3");
                else
                    jq.setQuartile("Q3-Q4");

                jq.setReputation(if_ > 5 ? "Hạng nhất" : if_ > 2 ? "Uy tín" : "Trung bình");
            } else {
                jq.setQuartile("Unknown");
                jq.setReputation("Unknown");
            }
        } else if (paper.getConference() != null) {
            jq.setVenueName(paper.getConference().getName());
            jq.setQuartile("N/A (Conference)");
            jq.setReputation("Conference");
        }

        return jq;
    }

    private FilteredPaperDTO.AuthorCredibility assessAuthorCredibility(Paper paper) {
        FilteredPaperDTO.AuthorCredibility ac = new FilteredPaperDTO.AuthorCredibility();

        if (paper.getPaperAuthors() != null && !paper.getPaperAuthors().isEmpty()) {
            ac.setTotalAuthors(paper.getPaperAuthors().size());

            var leadAuthor = paper.getPaperAuthors().stream()
                    .filter(pa -> pa.getAuthorOrder() == 1)
                    .findFirst();

            if (leadAuthor.isPresent()) {
                var author = leadAuthor.get().getAuthor();
                ac.setLeadAuthor(author.getFirstname() + " " + author.getLastname());
            }

            // Check international collaboration
            long uniqueCountries = paper.getPaperAuthors().stream()
                    .map(pa -> pa.getAuthor().getAffiliation())
                    .filter(aff -> aff != null && aff.getCountry() != null)
                    .map(aff -> aff.getCountry())
                    .distinct()
                    .count();

            ac.setHasInternationalCollaboration(uniqueCountries > 1);
            ac.setAffiliationType("Academic"); // Default
        }

        return ac;
    }

    private FilteredPaperDTO.RecencyScore assessRecency(Paper paper) {
        FilteredPaperDTO.RecencyScore rs = new FilteredPaperDTO.RecencyScore();

        int years = getYearsSincePublication(paper);
        rs.setYearsSincePublication(years);

        if (years == 0)
            rs.setFreshnessLabel("Đang xuất bản");
        else if (years <= 2)
            rs.setFreshnessLabel("Mới");
        else if (years <= 5)
            rs.setFreshnessLabel("Khá mới");
        else
            rs.setFreshnessLabel("Cũ");

        rs.setIsTrending(years <= 1); // Simplified

        return rs;
    }

    private FilteredPaperDTO.ImpactMetrics calculateImpactMetrics(Paper paper) {
        FilteredPaperDTO.ImpactMetrics im = new FilteredPaperDTO.ImpactMetrics();

        Long citations = paper.getCitationCount() != null ? paper.getCitationCount() : 0;
        int years = getYearsSincePublication(paper);

        im.setCitationCount(citations);

        if (years > 0) {
            float citPerYear = citations.floatValue() / years;
            im.setCitationsPerYear(citPerYear);

            if (years <= 2 && citations > 10)
                im.setCitationTrend("Đang tăng");
            else if (citations > 5)
                im.setCitationTrend("Ổn định");
            else
                im.setCitationTrend("Quá mới");
        } else {
            im.setCitationsPerYear(0f);
            im.setCitationTrend("Too New");
        }

        im.setIsHighlyInfuential(
                citations > 100 || (im.getCitationsPerYear() != null && im.getCitationsPerYear() > 20));

        return im;
    }

    private int calculateOverallScore(FilteredPaperDTO.QualityAssessment assessment) {
        int score = 50; // Base score

        // Journal quality (+0 to +25)
        var jq = assessment.getJournalQuality();
        if (jq.getImpactFactor() != null) {
            float if_ = jq.getImpactFactor();
            score += Math.min(25, (int) (if_ * 2.5));
        }

        // Impact metrics (+0 to +25)
        var im = assessment.getImpactMetrics();
        if (Boolean.TRUE.equals(im.getIsHighlyInfuential()))
            score += 25;
        else if (im.getCitationCount() != null && im.getCitationCount() > 20)
            score += 15;
        else if (im.getCitationCount() != null && im.getCitationCount() > 5)
            score += 10;

        // Recency (+0 to +15)
        var rs = assessment.getRecencyScore();
        if (rs.getYearsSincePublication() != null && rs.getYearsSincePublication() <= 2)
            score += 15;
        else if (rs.getYearsSincePublication() != null && rs.getYearsSincePublication() <= 5)
            score += 10;

        // Author credibility (+0 to +10)
        var ac = assessment.getAuthorCredibility();
        if (Boolean.TRUE.equals(ac.getHasInternationalCollaboration()))
            score += 10;
        else if (ac.getTotalAuthors() != null && ac.getTotalAuthors() > 3)
            score += 5;

        // Penalties
        if (rs.getYearsSincePublication() != null && rs.getYearsSincePublication() == 0)
            score -= 20; // Too new, unproven

        return Math.max(0, Math.min(100, score));
    }

    private String getScoreLabel(int score) {
        if (score >= 80)
            return "Xuất sắc";
        if (score >= 65)
            return "Tốt";
        if (score >= 45)
            return "Trung bình";
        if (score >= 30)
            return "Yếu";
        return "Thiếu dữ liệu";
    }

    private int getYearsSincePublication(Paper paper) {
        if (paper.getPublicationDate() == null)
            return 0;
        try {
            int year = Integer.parseInt(paper.getPublicationDate().substring(0, 4));
            return 2025 - year;
        } catch (Exception e) {
            return 0;
        }
    }

    private int getPublicationYear(Paper paper) {
        if (paper.getPublicationDate() == null)
            return 2025;
        try {
            return Integer.parseInt(paper.getPublicationDate().substring(0, 4));
        } catch (Exception e) {
            return 2025;
        }
    }
}
