package com.paperapi.paper_api.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.paperapi.paper_api.dto.PaperResponseDTO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(PaperService.class);
    private final RestTemplate restTemplate;
    private static final String OPENALEX_API_URL = "https://api.openalex.org/works/doi:";
    private static final String UNPAYWALL_API_URL = "https://api.unpaywall.org/v2/";
    private static final String UNPAYWALL_EMAIL = "your-email@example.com"; // Thay bằng email thật
    private static final String EUROPE_PMC_API_URL = "https://www.ebi.ac.uk/europepmc/webservices/rest/search";

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

        // Abstract (chỉ lưu vào abstractText field, KHÔNG dùng cho contentPreview)
        if (work.abstractInvertedIndex != null) {
            String abs = reconstructAbstract(work.abstractInvertedIndex);
            if (abs != null && !abs.isBlank()) {
                log.info("Reconstructed abstract, length: {}", abs.length());
                dto.setAbstractText(abs);
                log.info("Abstract saved to abstractText field");
            }
        } else {
            log.info("No abstract_inverted_index available");
        }

        // ƯU TIÊN: Lấy fulltext từ DOI trước (qua Unpaywall/Europe PMC)
        log.info("Attempting to extract fulltext from DOI sources...");
        String fulltext = tryGetFulltextFromDoi(dto.getDoi());
        
        if (fulltext != null && !fulltext.isBlank()) {
            log.info("Successfully retrieved fulltext from DOI, length: {} chars", fulltext.length());
            List<String> highlights = extractTopHighlightsFromFullText(fulltext, dto.getKeywords(), 20);
            if (highlights != null && !highlights.isEmpty()) {
                log.info("Extracted {} highlights from DOI fulltext", highlights.size());
                dto.setHighlights(highlights);
                
                // Lấy preview từ đầu bài
                String intro = extractIntroPreview(fulltext);
                if (intro != null && !intro.isBlank()) {
                    dto.setContentPreview(intro.length() > 500 ? intro.substring(0, 500) + "..." : intro);
                    log.info("Set contentPreview from DOI fulltext introduction");
                }
            }
        } else {
            // Fallback: Đọc từ PDF nếu không lấy được fulltext từ DOI
            log.info("No fulltext from DOI, trying PDF fallback...");
            if (dto.getPdfUrl() != null) {
                log.info("Extracting highlights and preview from full PDF: {}", dto.getPdfUrl());
                List<String> pdfHL = tryExtractHighlightsFromPdf(dto.getPdfUrl(), dto.getKeywords());
                if (pdfHL != null && !pdfHL.isEmpty()) {
                    log.info("Successfully extracted {} highlights from PDF", pdfHL.size());
                    dto.setHighlights(pdfHL);
                    
                    // Lấy contentPreview từ đầu bài PDF (lastExtractedIntroText)
                    String intro = lastExtractedIntroText;
                    if (intro != null && !intro.isBlank()) {
                        dto.setContentPreview(intro.length() > 500 ? intro.substring(0, 500) + "..." : intro);
                        log.info("Set contentPreview from PDF introduction");
                    }
                } else {
                    log.warn("Failed to extract highlights from PDF");
                }
            } else {
                log.info("No PDF URL available");
            }
        }
        
        // FALLBACK CUỐI CÙNG: Nếu vẫn chưa có highlights, dùng abstract
        if (dto.getHighlights() == null || dto.getHighlights().isEmpty()) {
            log.info("No highlights from DOI/PDF, trying to extract from abstract...");
            if (dto.getAbstractText() != null && !dto.getAbstractText().isBlank()) {
                List<String> abstractHL = extractTopHighlightsFromFullText(dto.getAbstractText(), dto.getKeywords(), 10);
                if (abstractHL != null && !abstractHL.isEmpty()) {
                    log.info("Extracted {} highlights from abstract", abstractHL.size());
                    dto.setHighlights(abstractHL);
                } else {
                    log.warn("Failed to extract highlights from abstract");
                }
            } else {
                log.warn("No abstract available for highlights extraction");
            }
        }
        
        // Fallback preview nếu không có PDF hoặc PDF fail
        if (dto.getContentPreview() == null || dto.getContentPreview().isBlank()) {
            log.info("No PDF preview available, trying fallback");
            
            // Thử dùng abstract nếu có
            if (dto.getAbstractText() != null && !dto.getAbstractText().isBlank()) {
                String abs = dto.getAbstractText();
                dto.setContentPreview(abs.length() > 500 ? abs.substring(0, 500) + "..." : abs);
                log.info("Using abstract as fallback contentPreview");
            } else {
                // Cuối cùng mới dùng metadata summary
                log.info("Building metadata summary as last fallback");
                String summary = buildFallbackSummary(work, dto.getKeywords());
                if (summary != null && !summary.isBlank()) {
                    dto.setContentPreview(summary.length() > 500 ? summary.substring(0, 500) + "..." : summary);
                    log.info("Fallback metadata summary set as contentPreview");
                }
            }
        }
        
        // Log final state
        if (dto.getHighlights() == null) {
            log.warn("Final highlights is NULL - no abstract and no PDF available");
        } else {
            log.info("Final highlights count: {}", dto.getHighlights().size());
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

    // --------- Fulltext extraction from DOI ---------
    
    /**
     * Lấy fulltext từ DOI qua nhiều nguồn: Direct HTML scraping, Unpaywall, Europe PMC
     */
    private String tryGetFulltextFromDoi(String doi) {
        if (doi == null || doi.isBlank()) return null;
        
        // Loại bỏ prefix "https://doi.org/" nếu có
        String cleanDoi = doi.replace("https://doi.org/", "").trim();
        
        // 1. THỬ SCRAPE TRỰC TIẾP HTML từ publisher qua DOI redirect
        log.info("Trying direct HTML scraping from DOI: {}", cleanDoi);
        String htmlText = tryScrapeDOIFulltext(cleanDoi);
        if (htmlText != null && !htmlText.isBlank()) {
            log.info("Got fulltext from DOI HTML scraping, length: {} chars", htmlText.length());
            return htmlText;
        }
        
        // 2. Thử Unpaywall API (cho open access papers)
        log.info("Trying Unpaywall API for DOI: {}", cleanDoi);
        String unpaywallText = tryUnpaywallFulltext(cleanDoi);
        if (unpaywallText != null && !unpaywallText.isBlank()) {
            log.info("Got fulltext from Unpaywall");
            return unpaywallText;
        }
        
        // 3. Thử Europe PMC API (cho biomedical papers)
        log.info("Trying Europe PMC API for DOI: {}", cleanDoi);
        String pmcText = tryEuropePmcFulltext(cleanDoi);
        if (pmcText != null && !pmcText.isBlank()) {
            log.info("Got fulltext from Europe PMC");
            return pmcText;
        }
        
        log.info("No fulltext available from DOI sources");
        return null;
    }
    
    /**
     * Scrape fulltext HTML trực tiếp từ publisher thông qua DOI redirect
     */
    private String tryScrapeDOIFulltext(String doi) {
        try {
            String doiUrl = "https://doi.org/" + doi;
            log.info("Scraping fulltext from: {}", doiUrl);
            
            // Sử dụng Jsoup để follow redirect và parse HTML
            Document doc = Jsoup.connect(doiUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(15000)
                    .followRedirects(true)
                    .get();
            
            String finalUrl = doc.location();
            log.info("DOI redirected to: {}", finalUrl);
            
            // Parse HTML dựa trên publisher (có nhận diện pattern)
            String fulltext = null;
            
            // 1. MDPI (mdpi.com)
            if (finalUrl.contains("mdpi.com")) {
                fulltext = parseMDPI(doc);
            }
            // 2. Springer/Nature (springer.com, nature.com)
            else if (finalUrl.contains("springer.com") || finalUrl.contains("nature.com")) {
                fulltext = parseSpringer(doc);
            }
            // 3. IEEE (ieee.org, ieeexplore.ieee.org)
            else if (finalUrl.contains("ieee.org")) {
                fulltext = parseIEEE(doc);
            }
            // 4. Elsevier/ScienceDirect (sciencedirect.com)
            else if (finalUrl.contains("sciencedirect.com")) {
                fulltext = parseScienceDirect(doc);
            }
            // 5. Wiley (wiley.com, onlinelibrary.wiley.com)
            else if (finalUrl.contains("wiley.com")) {
                fulltext = parseWiley(doc);
            }
            // 6. PLOS (plos.org, plosone.org)
            else if (finalUrl.contains("plos")) {
                fulltext = parsePLOS(doc);
            }
            // 7. Frontiers (frontiersin.org)
            else if (finalUrl.contains("frontiersin.org")) {
                fulltext = parseFrontiers(doc);
            }
            // 8. Generic fallback - lấy tất cả <p>, <section>, <article> tags
            else {
                fulltext = parseGeneric(doc);
            }
            
            if (fulltext != null && fulltext.length() > 500) {
                log.info("Successfully scraped fulltext from {}, length: {} chars", finalUrl, fulltext.length());
                return fulltext;
            } else {
                log.warn("Scraped text too short or empty from {}", finalUrl);
            }
            
        } catch (Exception e) {
            log.warn("Failed to scrape DOI HTML: {}", e.getMessage());
        }
        return null;
    }
    
    // ========== Parser cho từng publisher ==========
    
    private String parseMDPI(Document doc) {
        StringBuilder text = new StringBuilder();
        // MDPI thường dùng class="html-body" hoặc article tag
        Elements sections = doc.select("article.article-body, div.html-body, section.html-body");
        if (sections.isEmpty()) {
            sections = doc.select("div.article-content");
        }
        for (Element section : sections) {
            text.append(section.text()).append("\n\n");
        }
        return text.length() > 0 ? text.toString() : null;
    }
    
    private String parseSpringer(Document doc) {
        StringBuilder text = new StringBuilder();
        // Springer/Nature dùng id="body", class="c-article-body"
        Elements sections = doc.select("div.c-article-body, div#body, article.c-article");
        if (sections.isEmpty()) {
            sections = doc.select("div.article-body, section.article-section");
        }
        for (Element section : sections) {
            text.append(section.text()).append("\n\n");
        }
        return text.length() > 0 ? text.toString() : null;
    }
    
    private String parseIEEE(Document doc) {
        StringBuilder text = new StringBuilder();
        // IEEE dùng class="article-content" or div.article
        Elements sections = doc.select("div.article-content, div.article, xpl-article-content");
        if (sections.isEmpty()) {
            sections = doc.select("div.section");
        }
        for (Element section : sections) {
            text.append(section.text()).append("\n\n");
        }
        return text.length() > 0 ? text.toString() : null;
    }
    
    private String parseScienceDirect(Document doc) {
        StringBuilder text = new StringBuilder();
        // ScienceDirect dùng id="body", class="Body"
        Elements sections = doc.select("div#body, div.Body, div.article-body");
        if (sections.isEmpty()) {
            sections = doc.select("section.body, div.sections");
        }
        for (Element section : sections) {
            text.append(section.text()).append("\n\n");
        }
        return text.length() > 0 ? text.toString() : null;
    }
    
    private String parseWiley(Document doc) {
        StringBuilder text = new StringBuilder();
        // Wiley dùng class="article-section__content"
        Elements sections = doc.select("section.article-section, div.article-section__content");
        if (sections.isEmpty()) {
            sections = doc.select("div.article-body");
        }
        for (Element section : sections) {
            text.append(section.text()).append("\n\n");
        }
        return text.length() > 0 ? text.toString() : null;
    }
    
    private String parsePLOS(Document doc) {
        StringBuilder text = new StringBuilder();
        // PLOS dùng class="article-text"
        Elements sections = doc.select("div.article-text, div#artText");
        if (sections.isEmpty()) {
            sections = doc.select("div.article-content");
        }
        for (Element section : sections) {
            text.append(section.text()).append("\n\n");
        }
        return text.length() > 0 ? text.toString() : null;
    }
    
    private String parseFrontiers(Document doc) {
        StringBuilder text = new StringBuilder();
        // Frontiers dùng class="JournalFullText"
        Elements sections = doc.select("div.JournalFullText, div.article-content");
        if (sections.isEmpty()) {
            sections = doc.select("section.article-section");
        }
        for (Element section : sections) {
            text.append(section.text()).append("\n\n");
        }
        return text.length() > 0 ? text.toString() : null;
    }
    
    /**
     * Generic parser - lấy tất cả text từ article/section/p tags
     */
    private String parseGeneric(Document doc) {
        StringBuilder text = new StringBuilder();
        
        // Thử lấy từ article tag trước
        Elements articles = doc.select("article");
        if (!articles.isEmpty()) {
            for (Element article : articles) {
                text.append(article.text()).append("\n\n");
            }
            return text.toString();
        }
        
        // Thử lấy từ các section tags
        Elements sections = doc.select("section");
        if (!sections.isEmpty()) {
            for (Element section : sections) {
                text.append(section.text()).append("\n\n");
            }
            return text.toString();
        }
        
        // Cuối cùng lấy tất cả paragraphs trong main/body
        Elements mainContent = doc.select("main p, div#content p, div.content p, body p");
        for (Element p : mainContent) {
            String para = p.text().trim();
            if (para.length() > 50) { // chỉ lấy paragraph có nội dung
                text.append(para).append("\n\n");
            }
        }
        
        return text.length() > 500 ? text.toString() : null;
    }
    
    /**
     * Lấy fulltext từ Unpaywall API
     */
    private String tryUnpaywallFulltext(String doi) {
        try {
            String url = UNPAYWALL_API_URL + doi + "?email=" + UNPAYWALL_EMAIL;
            
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "Paper-API/1.0 (mailto:" + UNPAYWALL_EMAIL + ")")
                    .GET()
                    .build();
            
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                String json = response.body();
                // Parse JSON để lấy best_oa_location.url_for_landing_page
                // Sau đó scrape HTML từ landing page để lấy fulltext
                // (Implementation phức tạp, cần parse HTML của từng publisher)
                
                // Hiện tại: trả về null, sẽ fallback sang PDF
                // TODO: Implement HTML scraping cho các publisher phổ biến
                log.info("Unpaywall returned metadata, but HTML scraping not implemented yet");
                return null;
            }
        } catch (Exception e) {
            log.warn("Unpaywall API error: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Lấy fulltext từ Europe PMC API
     */
    private String tryEuropePmcFulltext(String doi) {
        try {
            // Europe PMC hỗ trợ lấy fulltext XML cho bài open access
            String searchUrl = EUROPE_PMC_API_URL + "?query=DOI:" + doi + "&format=json";
            
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(searchUrl))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "Paper-API/1.0")
                    .GET()
                    .build();
            
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                String json = response.body();
                
                // Parse JSON để lấy PMCID
                String pmcId = extractPmcIdFromJson(json);
                if (pmcId != null) {
                    // Lấy fulltext XML từ PMC
                    return fetchPmcFulltext(pmcId);
                }
            }
        } catch (Exception e) {
            log.warn("Europe PMC API error: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Extract PMC ID từ Europe PMC search response
     */
    private String extractPmcIdFromJson(String json) {
        try {
            // Simple regex extraction (có thể dùng Jackson để parse properly)
            if (json.contains("\"pmcid\":\"")) {
                int start = json.indexOf("\"pmcid\":\"") + 9;
                int end = json.indexOf("\"", start);
                if (end > start) {
                    return json.substring(start, end);
                }
            }
        } catch (Exception e) {
            log.warn("Error extracting PMC ID: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Lấy fulltext từ PMC bằng PMC ID
     */
    private String fetchPmcFulltext(String pmcId) {
        try {
            String fulltextUrl = "https://www.ebi.ac.uk/europepmc/webservices/rest/" + pmcId + "/fullTextXML";
            
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(fulltextUrl))
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", "Paper-API/1.0")
                    .GET()
                    .build();
            
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                String xml = response.body();
                // Parse XML và extract text từ <body> tags
                return extractTextFromPmcXml(xml);
            }
        } catch (Exception e) {
            log.warn("Error fetching PMC fulltext: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Extract plain text từ Europe PMC XML
     */
    private String extractTextFromPmcXml(String xml) {
        if (xml == null || xml.isBlank()) return null;
        
        try {
            // Simple XML text extraction (loại bỏ tags)
            // Lấy nội dung từ <body> tag
            String text = xml;
            
            // Tìm phần body
            int bodyStart = text.indexOf("<body>");
            int bodyEnd = text.indexOf("</body>");
            
            if (bodyStart != -1 && bodyEnd != -1 && bodyEnd > bodyStart) {
                text = text.substring(bodyStart + 6, bodyEnd);
            }
            
            // Loại bỏ tất cả XML tags
            text = text.replaceAll("<[^>]+>", " ");
            
            // Loại bỏ khoảng trắng thừa
            text = text.replaceAll("\\s+", " ").trim();
            
            log.info("Extracted text from PMC XML, length: {} chars", text.length());
            return text.length() > 100 ? text : null;
            
        } catch (Exception e) {
            log.warn("Error parsing PMC XML: {}", e.getMessage());
            return null;
        }
    }

    // --------- PDF full-text extraction & section-aware highlights ---------
    private volatile String lastExtractedIntroText; // cache ngắn cho preview

    /**
     * Lấy đoạn đầu bài từ full text để làm contentPreview
     */
    private String extractIntroPreview(String fullText) {
        if (fullText == null || fullText.isBlank()) return null;
        
        // Tìm phần Introduction hoặc lấy đoạn đầu
        String[] paragraphs = fullText.split("\\n\\n+");
        StringBuilder preview = new StringBuilder();
        
        boolean foundIntro = false;
        for (String para : paragraphs) {
            String lower = para.trim().toLowerCase();
            
            // Bỏ qua header/metadata/title page
            if (lower.length() < 100) continue;
            if (lower.contains("abstract")) {
                foundIntro = true; // sau abstract thường là introduction
                continue;
            }
            
            // Lấy paragraph đầu tiên có nội dung
            if (!foundIntro && para.trim().length() >= 100) {
                preview.append(para.trim());
                break;
            }
            
            if (foundIntro && para.trim().length() >= 100) {
                preview.append(para.trim());
                break;
            }
        }
        
        // Nếu không tìm được, lấy 500-1000 ký tự đầu
        if (preview.length() == 0) {
            return fullText.substring(0, Math.min(1000, fullText.length())).trim();
        }
        
        return preview.toString();
    }

    /**
     * Lấy top 15-20 câu quan trọng nhất từ TOÀN BỘ bài báo (từ đầu đến cuối)
     */
    private List<String> extractTopHighlightsFromFullText(String fullText, List<String> keywords, int maxCount) {
        if (fullText == null || fullText.isBlank()) return null;
        
        // Tách toàn bộ text thành câu
        String[] sentences = fullText.split("(?<=[.!?])\\s+");
        List<ScoredSentence> scored = new ArrayList<>();
        
        for (String s : sentences) {
            String t = s.trim();
            // Lọc câu quá ngắn/dài, và câu header/metadata
            if (t.length() < 50 || t.length() > 500) continue;
            if (t.matches("^[0-9\\.\\s]+$")) continue; // bỏ số trang
            if (t.toLowerCase().startsWith("figure ") || t.toLowerCase().startsWith("table ")) continue;
            if (t.toLowerCase().contains("©") || t.toLowerCase().contains("rights reserved")) continue;
            if (t.toLowerCase().startsWith("www.") || t.toLowerCase().startsWith("http")) continue;
            if (t.toLowerCase().contains("doi:")) continue;
            
            double score = scoreSentence(t, keywords);
            if (score > 0) {
                scored.add(new ScoredSentence(t, score));
            }
        }
        
        // Sort theo score và lấy top
        scored.sort((a, b) -> Double.compare(b.score, a.score));
        
        // Lấy 15-20 câu quan trọng nhất phủ toàn bộ bài, bỏ trùng lặp
        int cap = Math.max(15, Math.min(maxCount, 20));
        return scored.stream()
                .limit(cap * 2) // lấy nhiều hơn để filter trùng lặp
                .map(ss -> ss.sentence)
                .distinct()
                .limit(cap)
                .collect(Collectors.toList());
    }

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
                // Đọc TOÀN BỘ PDF (không giới hạn pages)
                PDFTextStripper stripper = new PDFTextStripper();
                String text = stripper.getText(doc);
                if (text == null || text.isBlank()) return null;

                log.info("Extracted full PDF text, length: {} chars, {} pages", text.length(), doc.getNumberOfPages());

                // Lưu đoạn đầu bài (~500-1000 chars) để làm contentPreview
                this.lastExtractedIntroText = extractIntroPreview(text);

                // Lấy top 5-10 câu quan trọng nhất từ TOÀN BỘ bài
                return extractTopHighlightsFromFullText(text, keywords, 10);
            }
        } catch (Exception ignore) {
            return null;
        }
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
        
        // Key phrases indicating important findings/contributions (điểm cao)
        List<String> highValuePhrases = Arrays.asList(
            "we propose", "we introduce", "we present", "we demonstrate", "we show",
            "our method", "our approach", "our results", "our findings", "our contribution",
            "this paper", "this study", "this work", "this research",
            "significant", "novel", "innovative", "improve", "outperform", "achieve",
            "conclude", "conclusion", "in conclusion", "contribution", "key finding",
            "main result", "important", "critical", "essential", "fundamental"
        );
        
        // Medium value phrases (điểm trung bình)
        List<String> mediumValuePhrases = Arrays.asList(
            "results show", "results indicate", "results suggest",
            "accuracy", "performance", "effectiveness", "efficiency",
            "compared to", "compared with", "better than", "superior to",
            "method", "algorithm", "framework", "model", "approach",
            "experiment", "evaluation", "analysis", "implementation"
        );
        
        // Introduction/Background phrases (điểm thấp nhưng vẫn quan trọng)
        List<String> contextPhrases = Arrays.asList(
            "recent", "previous", "existing", "traditional",
            "challenge", "problem", "issue", "limitation",
            "objective", "goal", "aim", "purpose"
        );
        
        double score = 0;
        
        // High-value phrases (main contributions, findings)
        for (String phrase : highValuePhrases) {
            if (lower.contains(phrase)) score += 4.0;
        }
        
        // Medium-value phrases
        for (String phrase : mediumValuePhrases) {
            if (lower.contains(phrase)) score += 2.0;
        }
        
        // Context phrases
        for (String phrase : contextPhrases) {
            if (lower.contains(phrase)) score += 1.0;
        }
        
        // Keywords from paper metadata (rất quan trọng)
        if (keywords != null) {
            for (String kw : keywords) {
                if (kw != null && lower.contains(kw.toLowerCase())) score += 3.0;
            }
        }
        
        // Câu có số liệu/phần trăm (findings thường có số)
        if (lower.matches(".*\\b[0-9]+(\\.[0-9]+)?%.*")) score += 3.0;
        if (lower.matches(".*\\b[0-9]+(\\.[0-9]+)?\\s*(accuracy|precision|recall|f1|error|rate).*")) score += 4.0;
        
        // Câu có so sánh số liệu
        if (lower.contains("increase") || lower.contains("decrease") || 
            lower.contains("higher") || lower.contains("lower") ||
            lower.contains("faster") || lower.contains("slower")) {
            score += 2.0;
        }
        
        // Bonus cho câu có cấu trúc academic tốt (không quá ngắn, không quá dài)
        if (s.length() >= 80 && s.length() <= 300) {
            score += 0.5;
        }
        
        return score;
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
