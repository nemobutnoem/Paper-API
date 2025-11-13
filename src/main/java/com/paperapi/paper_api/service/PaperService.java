package com.paperapi.paper_api.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.paperapi.paper_api.dto.PaperResponseDTO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PaperService {

    private final RestTemplate restTemplate;
    private static final String OPENALEX_API_URL = "https://api.openalex.org/works/doi:";

    public PaperService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public PaperResponseDTO getPaperInfoByDoi(String doi) {
        String url = OPENALEX_API_URL + doi;
        OpenAlexWork response = restTemplate.getForObject(url, OpenAlexWork.class);

        if (response == null) {
            return null;
        }

        return mapToPaperResponseDTO(response);
    }

    private PaperResponseDTO mapToPaperResponseDTO(OpenAlexWork work) {
        PaperResponseDTO dto = new PaperResponseDTO();
        dto.setTitle(work.title);
        dto.setPublicationYear(work.publicationYear);
        dto.setCitationCount(work.citedByCount);
        // DOI
        if (work.doi != null && !work.doi.isBlank()) {
            dto.setDoi(work.doi);
        }
        
        // Publication date
        if (work.publicationDate != null) {
            dto.setPublicationDate(work.publicationDate);
        }

        // PDF URL (with fallbacks)
        if (work.primaryLocation != null && work.primaryLocation.pdfUrl != null) {
            dto.setPdfUrl(work.primaryLocation.pdfUrl);
        } else if (work.bestOaLocation != null && work.bestOaLocation.pdfUrl != null) {
            dto.setPdfUrl(work.bestOaLocation.pdfUrl);
        } else if (work.openAccess != null && work.openAccess.oaUrl != null && work.openAccess.oaUrl.toLowerCase().endsWith(".pdf")) {
            dto.setPdfUrl(work.openAccess.oaUrl);
        }

        // Landing page URL (with fallbacks)
        String landing = null;
        if (work.primaryLocation != null && work.primaryLocation.landingPageUrl != null) {
            landing = work.primaryLocation.landingPageUrl;
        } else if (work.bestOaLocation != null && work.bestOaLocation.landingPageUrl != null) {
            landing = work.bestOaLocation.landingPageUrl;
        } else if (work.openAccess != null && work.openAccess.oaUrl != null && !work.openAccess.oaUrl.toLowerCase().endsWith(".pdf")) {
            landing = work.openAccess.oaUrl;
        }
        if (landing != null) {
            dto.setLandingPageUrl(landing);
        }

        // Open access flag (with fallback)
        if (work.openAccess != null) {
            dto.setIsOpenAccess(work.openAccess.isOa);
        } else if (work.primaryLocation != null) {
            dto.setIsOpenAccess(work.primaryLocation.isOa);
        }

        // Authors with affiliations
        if (work.authorships != null) {
            dto.setAuthors(work.authorships.stream().map(authorship -> {
                var authorDto = new com.paperapi.paper_api.dto.AuthorDTO();
                
                // Parse name into first and last name
                if (authorship.author.displayName != null) {
                    String[] nameParts = authorship.author.displayName.split(" ", 2);
                    authorDto.setFirstName(nameParts.length > 0 ? nameParts[0] : "");
                    authorDto.setLastName(nameParts.length > 1 ? nameParts[1] : "");
                }
                
                authorDto.setOrcId(authorship.author.orcid);
                
                // Affiliation
                if (authorship.institutions != null && !authorship.institutions.isEmpty()) {
                    var institution = authorship.institutions.get(0);
                    var affiliationDto = new com.paperapi.paper_api.dto.AffiliationDTO();
                    affiliationDto.setName(institution.displayName);
                    affiliationDto.setCity(institution.city);
                    affiliationDto.setCountry(institution.countryCode);
                    authorDto.setAffiliation(affiliationDto);
                }
                
                return authorDto;
            }).collect(Collectors.toList()));
        }

        // Journal information
        if (work.primaryLocation != null && work.primaryLocation.source != null) {
            var source = work.primaryLocation.source;
            if ("journal".equalsIgnoreCase(source.type)) {
                var journalDto = new com.paperapi.paper_api.dto.JournalDTO();
                journalDto.setTitle(source.displayName);
                journalDto.setIssn(source.issn);
                
                // Publisher (if available)
                if (source.publisher != null) {
                    var publisherDto = new com.paperapi.paper_api.dto.PublisherDTO();
                    publisherDto.setName(source.publisher);
                    journalDto.setPublisher(publisherDto);
                }
                
                dto.setJournal(journalDto);
                
                // Volume information
                if (work.biblio != null) {
                    var volumeDto = new com.paperapi.paper_api.dto.VolumeDTO();
                    volumeDto.setVolumeNumber(work.biblio.volume);
                    volumeDto.setIssueNumber(work.biblio.issue);
                    volumeDto.setPublicationYear(work.publicationYear);
                    dto.setVolume(volumeDto);
                }
            }
        }

        // Keywords from concepts (SET TRƯỚC để dùng cho highlights)
        List<String> keywords = null;
        if (work.concepts != null && !work.concepts.isEmpty()) {
            keywords = work.concepts.stream()
                .map(concept -> concept.displayName)
                .collect(Collectors.toList());
            dto.setKeywords(keywords);
            
            // Research fields (top level concepts)
            var fields = work.concepts.stream()
                .filter(c -> c.level == 0 || c.level == 1)
                .map(concept -> {
                    var fieldDto = new com.paperapi.paper_api.dto.ResearchFieldDTO();
                    fieldDto.setFieldName(concept.displayName);
                    fieldDto.setDescription("Level " + concept.level + " research area");
                    return fieldDto;
                }).collect(Collectors.toList());
            if (!fields.isEmpty()) {
                dto.setResearchFields(fields);
            }
        }

        // Abstract (reconstruct và tạo highlights)
        if (work.abstractInvertedIndex != null) {
            String abs = reconstructAbstract(work.abstractInvertedIndex);
            if (abs != null && !abs.isBlank()) {
                dto.setAbstractText(abs);
                // Provide a short preview (first ~500 chars)
                dto.setContentPreview(abs.length() > 500 ? abs.substring(0, 500) + "..." : abs);
                // Create highlights from abstract sentences
                List<String> hs = extractHighlights(abs, 10, keywords);
                hs = filterNonContentHighlights(hs, work.title);
                // Nếu filter loại hết, lấy lại từ abstract không filter
                if (hs.isEmpty()) {
                    hs = extractHighlights(abs, 10, keywords);
                }
                if (!hs.isEmpty()) {
                    dto.setHighlights(hs);
                }
            }
        }

        // Fallback preview/highlights khi thiếu abstract
        if (dto.getContentPreview() == null || dto.getContentPreview().isBlank()) {
            String summary = buildFallbackSummary(work, dto.getKeywords());
            if (summary != null && !summary.isBlank()) {
                dto.setContentPreview(summary.length() > 500 ? summary.substring(0, 500) + "..." : summary);
                List<String> hs = extractHighlights(summary, 10, dto.getKeywords());
                hs = filterNonContentHighlights(hs, work.title);
                // Nếu không có highlights hợp lệ, không set (để PDF fallback xử lý)
                if (!hs.isEmpty()) {
                    dto.setHighlights(hs);
                }
            }
        }

        // Nếu highlights vẫn ít hoặc rỗng và có PDF, thử trích xuất từ toàn văn PDF
        if ((dto.getHighlights() == null || dto.getHighlights().size() < 5)
                && dto.getPdfUrl() != null) {
            List<String> pdfHL = tryExtractHighlightsFromPdf(dto.getPdfUrl(), dto.getKeywords());
            if (pdfHL != null && !pdfHL.isEmpty()) {
                dto.setHighlights(pdfHL);
                // Nếu chưa có contentPreview, lấy đoạn mở đầu từ phần Introduction
                if (dto.getContentPreview() == null || dto.getContentPreview().isBlank()) {
                    String intro = lastExtractedIntroText;
                    if (intro != null && !intro.isBlank()) {
                        dto.setContentPreview(intro.length() > 500 ? intro.substring(0, 500) + "..." : intro);
                    }
                }
            }
        }

        return dto;
    }

    // Reconstruct abstract text from OpenAlex's abstract_inverted_index structure
    private String reconstructAbstract(Object invertedObj) {
        if (!(invertedObj instanceof Map)) return null;
        @SuppressWarnings("unchecked")
        Map<String, Object> inverted = (Map<String, Object>) invertedObj;

        int maxIndex = -1;
        for (Object v : inverted.values()) {
            if (v instanceof List<?>) {
                for (Object o : (List<?>) v) {
                    if (o instanceof Number) {
                        maxIndex = Math.max(maxIndex, ((Number) o).intValue());
                    }
                }
            }
        }
        if (maxIndex < 0) return null;

        String[] words = new String[maxIndex + 1];
        for (Map.Entry<String, Object> e : inverted.entrySet()) {
            String token = e.getKey();
            Object v = e.getValue();
            if (v instanceof List<?>) {
                for (Object o : (List<?>) v) {
                    if (o instanceof Number) {
                        int idx = ((Number) o).intValue();
                        if (idx >= 0 && idx < words.length) words[idx] = token;
                    }
                }
            }
        }
        return Arrays.stream(words).filter(Objects::nonNull).collect(Collectors.joining(" "));
    }

    // Extract 5-10 highlight sentences from abstract using simple keyword heuristics
    private List<String> extractHighlights(String abstractText, int maxCount, List<String> keywords) {
        if (abstractText == null || abstractText.isBlank()) return Collections.emptyList();
        String[] sentences = abstractText.split("(?<=[.!?])\\s+");
        List<String> keyPhrases = Arrays.asList("we ", "this paper", "our method", "results", "propose", "introduce", "conclude", "outperform", "significant");

        List<String> ranked = new ArrayList<>();
        // 1) Prefer sentences containing key phrases
        for (String s : sentences) {
            String lower = s.toLowerCase();
            boolean good = keyPhrases.stream().anyMatch(lower::contains);
            if (!good && keywords != null) {
                for (String kw : keywords) {
                    if (kw != null && !kw.isBlank() && lower.contains(kw.toLowerCase())) { good = true; break; }
                }
            }
            if (good) ranked.add(s.trim());
            if (ranked.size() >= maxCount) break;
        }
        // 2) If not enough, fill from the beginning
        for (String s : sentences) {
            if (ranked.size() >= maxCount) break;
            String t = s.trim();
            if (!t.isEmpty() && !ranked.contains(t)) ranked.add(t);
        }
        // Limit to 5-10 items
        int cap = Math.max(5, Math.min(maxCount, 10));
        return ranked.stream().limit(cap).collect(Collectors.toList());
    }

    // Build a short readable summary when abstract is missing
    private String buildFallbackSummary(OpenAlexWork w, List<String> keywords) {
        if (w == null) return null;
        StringBuilder sb = new StringBuilder();
        if (w.title != null && !w.title.isBlank()) {
            sb.append(w.title.trim());
            if (!sb.toString().endsWith(".")) sb.append(".");
            sb.append(' ');
        }
        if (keywords != null && !keywords.isEmpty()) {
            sb.append("Keywords: ");
            sb.append(keywords.stream().filter(Objects::nonNull).limit(5).collect(Collectors.joining(", ")));
            sb.append(". ");
        }
        if (w.primaryLocation != null && w.primaryLocation.source != null && w.primaryLocation.source.displayName != null) {
            sb.append("Published in ").append(w.primaryLocation.source.displayName);
            if (w.publicationYear > 0) sb.append(" (" + w.publicationYear + ")");
            sb.append(". ");
        } else if (w.publicationYear > 0) {
            sb.append("Published in ").append(w.publicationYear).append(". ");
        }
        if (w.citedByCount > 0) sb.append("Citations: ").append(w.citedByCount).append(". ");
        return sb.length() == 0 ? null : sb.toString().trim();
    }

    // --------- PDF full-text extraction & section-aware highlights ---------
    private volatile String lastExtractedIntroText; // cache ngắn cho preview

    private List<String> tryExtractHighlightsFromPdf(String pdfUrl, List<String> keywords) {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            URI uri = URI.create(pdfUrl);

            // HEAD để kiểm tra kích thước, bỏ qua file quá lớn (> 30MB)
            HttpRequest head = HttpRequest.newBuilder(uri)
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "Paper-API/1.0")
                    .build();
            HttpResponse<Void> headResp = client.send(head, HttpResponse.BodyHandlers.discarding());
            Optional<String> lenStr = headResp.headers().firstValue("content-length");
            if (lenStr.isPresent()) {
                long len = Long.parseLong(lenStr.get());
                if (len > 30L * 1024 * 1024) return null; // quá lớn
            }

            // GET tải nội dung
            HttpRequest get = HttpRequest.newBuilder(uri)
                    .GET()
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", "Paper-API/1.0")
                    .build();
            HttpResponse<InputStream> resp = client.send(get, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) return null;

            try (InputStream in = resp.body(); PDDocument doc = PDDocument.load(in)) {
                int endPage = Math.min(doc.getNumberOfPages(), 12); // chỉ 12 trang đầu
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(1);
                stripper.setEndPage(endPage);
                String text = stripper.getText(doc);
                if (text == null || text.isBlank()) return null;

                // Cắt theo mục và chọn câu
                SectionedText st = splitBySections(text);
                this.lastExtractedIntroText = st.introduction != null ? firstParagraph(st.introduction) : null;
                return extractSectionAwareHighlights(st, keywords, 10);
            }
        } catch (Exception ignore) {
            return null;
        }
    }

    private static class SectionedText {
        String introduction;
        String methods;
        String results;
        String discussion;
        String conclusion;
        String body;
    }

    private SectionedText splitBySections(String text) {
        SectionedText st = new SectionedText();
        String t = text.replace('\r', '\n');
        // Chuẩn hoá tiêu đề dòng
        String[] lines = t.split("\n");
        String curName = "body";
        Map<String, StringBuilder> map = new HashMap<>();
        map.put("introduction", new StringBuilder());
        map.put("methods", new StringBuilder());
        map.put("results", new StringBuilder());
        map.put("discussion", new StringBuilder());
        map.put("conclusion", new StringBuilder());
        map.put("body", new StringBuilder());

        for (String raw : lines) {
            String line = raw.trim();
            String lower = line.toLowerCase();
            if (line.length() <= 120 && (
                    lower.equals("introduction") || lower.startsWith("introduction ") ||
                    lower.equals("background") ||
                    lower.equals("materials and methods") || lower.equals("methods") || lower.equals("methodology") ||
                    lower.equals("results") ||
                    lower.equals("discussion") ||
                    lower.equals("conclusion") || lower.equals("conclusions") || lower.startsWith("conclusion ")
            )) {
                curName = sectionKey(lower);
                continue;
            }
            map.get(curName).append(line).append(' ');
        }
        st.introduction = map.get("introduction").toString().trim();
        // gộp background vào introduction
        // (ở logic hiện tại không tách background riêng)
        st.methods = map.get("methods").toString().trim();
        st.results = map.get("results").toString().trim();
        st.discussion = map.get("discussion").toString().trim();
        st.conclusion = map.get("conclusion").toString().trim();
        st.body = map.get("body").toString().trim();
        return st;
    }

    private String sectionKey(String lower) {
        if (lower.startsWith("abstract")) return "abstract";
        if (lower.startsWith("introduction") || lower.equals("background")) return "introduction";
        if (lower.contains("materials and methods") || lower.startsWith("methods") || lower.equals("methodology")) return "methods";
        if (lower.startsWith("results")) return "results";
        if (lower.startsWith("discussion")) return "discussion";
        if (lower.startsWith("conclusion")) return "conclusion";
        return "body";
    }

    private List<String> extractSectionAwareHighlights(SectionedText st, List<String> keywords, int maxTotal) {
        List<String> out = new ArrayList<>();
        addBestSentences(out, st.introduction, keywords, 2, 2);
        addBestSentences(out, st.methods, keywords, 2, 1);
        addBestSentences(out, st.results, keywords, 3, 3);
        addBestSentences(out, st.discussion, keywords, 2, 2);
        addBestSentences(out, st.conclusion, keywords, 2, 2);
        if (out.size() < Math.max(5, maxTotal)) {
            addBestSentences(out, st.body, keywords, 1, 1);
        }
        // rút gọn số lượng
        int cap = Math.max(5, Math.min(maxTotal, 10));
        return out.stream().filter(Objects::nonNull).distinct().limit(cap).collect(Collectors.toList());
    }

    private void addBestSentences(List<String> out, String sectionText, List<String> keywords, int strength, int count) {
        if (sectionText == null || sectionText.isBlank()) return;
        String[] sentences = sectionText.split("(?<=[.!?])\\s+");
        List<ScoredSentence> scored = new ArrayList<>();
        for (String s : sentences) {
            String t = s.trim();
            if (t.length() < 40 || t.length() > 300) continue;
            double sc = scoreSentence(t, keywords) * strength;
            scored.add(new ScoredSentence(t, sc));
        }
        scored.sort((a, b) -> Double.compare(b.score, a.score));
        scored.stream().limit(count).map(ss -> ss.sentence).forEach(out::add);
    }

    private static class ScoredSentence {
        String sentence;
        double score;
        ScoredSentence(String sentence, double score) {
            this.sentence = sentence;
            this.score = score;
        }
    }

    private double scoreSentence(String s, List<String> keywords) {
        String lower = s.toLowerCase();
        List<String> keyPhrases = Arrays.asList("we ", "this paper", "our method", "results", "propose", "introduce", "conclude", "significant", "%", "accuracy", "improve");
        double score = 0;
        for (String kp : keyPhrases) if (lower.contains(kp)) score += 1.5;
        if (keywords != null) {
            for (String kw : keywords) if (kw != null && lower.contains(kw.toLowerCase())) score += 1.0;
        }
        // ưu tiên câu có số liệu
        if (lower.matches(".*[0-9]+(\\.[0-9]+)?%?.*")) score += 0.5;
        return score;
    }

    private String firstParagraph(String text) {
        if (text == null) return null;
        String[] parts = text.split("\n\n|(?<=[.!?])\s{2,}");
        return parts.length > 0 ? parts[0].trim() : text.trim();
    }

    // Loại bỏ câu không thuộc nội dung (tiêu đề, metadata)
    private List<String> filterNonContentHighlights(List<String> hs, String title) {
        if (hs == null) return Collections.emptyList();
        String normTitle = normalizeForComparison(title);
        return hs.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .filter(s -> {
                    String normalized = normalizeForComparison(s);
                    if (normTitle != null) {
                        if (normalized.equals(normTitle)) return false; // bỏ tiêu đề nguyên bản
                        if (normalized.contains(normTitle)) return false; // bỏ câu chứa tiêu đề
                        if (normTitle.contains(normalized) && normalized.length() > 0) return false; // bỏ biến thể tiêu đề
                    }
                    String lower = s.toLowerCase();
                    if (lower.startsWith("keywords:")) return false;
                    if (lower.startsWith("published in")) return false;
                    if (lower.startsWith("citations:")) return false;
                    return true;
                })
                .collect(Collectors.toList());
    }

    private String normalizeForComparison(String input) {
        if (input == null) return null;
        String normalized = input.toLowerCase().replaceAll("[^a-z0-9]+", " ").trim();
        return normalized.replaceAll("\\s+", " ");
    }

    // --- Lớp nội bộ để hứng JSON từ OpenAlex ---
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class OpenAlexWork {
        public String title;
        public String doi;
        @JsonProperty("publication_year") public int publicationYear;
        @JsonProperty("publication_date") public String publicationDate;
        @JsonProperty("cited_by_count") public long citedByCount;
        @JsonProperty("primary_location") public Location primaryLocation;
        @JsonProperty("best_oa_location") public Location bestOaLocation;
        @JsonProperty("open_access") public OpenAccess openAccess;
        @JsonProperty("abstract_inverted_index") public Object abstractInvertedIndex;
        public List<Authorship> authorships;
        public List<Concept> concepts;
        public Biblio biblio;
    }
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Biblio {
        public String volume;
        public String issue;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Location {
        public Source source;
        @JsonProperty("pdf_url") public String pdfUrl;
        @JsonProperty("landing_page_url") public String landingPageUrl;
        @JsonProperty("is_oa") public Boolean isOa;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Source {
        @JsonProperty("display_name") public String displayName;
        public String type;
        @JsonProperty("issn_l") public String issn;
        public String publisher;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class OpenAccess {
        @JsonProperty("is_oa") public Boolean isOa;
        @JsonProperty("oa_url") public String oaUrl;
        @JsonProperty("oa_status") public String oaStatus;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Authorship {
        public Author author;
        public List<Institution> institutions;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Author {
        @JsonProperty("display_name") public String displayName;
        public String orcid;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Institution {
        @JsonProperty("display_name") public String displayName;
        public String city;
        @JsonProperty("country_code") public String countryCode;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Concept {
        @JsonProperty("display_name") public String displayName;
        public int level;
    }
}
