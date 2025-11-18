package com.paperapi.paper_api.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.paperapi.paper_api.dto.PaperResponseDTO;
import com.paperapi.paper_api.dto.FilteredPaperDTO;
import com.paperapi.paper_api.entity.Paper;
import com.paperapi.paper_api.repository.PaperRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PaperService {

    private final RestTemplate restTemplate;
    private final VectorService vectorService;
    private final PaperRepository paperRepository;
    private static final String OPENALEX_API_URL = "https://api.openalex.org/works/doi:";

    public PaperService(RestTemplate restTemplate, VectorService vectorService, PaperRepository paperRepository) {
        this.restTemplate = restTemplate;
        this.vectorService = vectorService;
        this.paperRepository = paperRepository;
    }

    public List<FilteredPaperDTO> findSimilarPapers(String query, int limit) {
        // 1. Create embedding for the query
        List<Double> embedding = vectorService.getEmbedding(query);
        if (embedding == null || embedding.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. Tạm thời bỏ tính năng vector search (PGvector)
        // TODO: Re-enable when pgvector dependency is properly configured.
        List<Paper> similarPapers = Collections.emptyList();

        // 4. Convert to DTO
        return similarPapers.stream()
                .map(this::mapToFilteredPaperDTO)
                .collect(Collectors.toList());
    }

    private FilteredPaperDTO mapToFilteredPaperDTO(Paper paper) {
        FilteredPaperDTO dto = new FilteredPaperDTO();
        dto.setPaperId(paper.getPaperId());
        dto.setTitle(paper.getTitle());
        dto.setDoi(paper.getDoi());

        // For simplicity, we'll just populate the core info for now.
        // A more complete implementation would re-use the MetadataService logic.
        FilteredPaperDTO.CoreInfo coreInfo = new FilteredPaperDTO.CoreInfo();
        coreInfo.setPublicationDate(paper.getPublicationDate());
        coreInfo.setCitationCount(paper.getCitationCount());
        coreInfo.setPdfUrl(paper.getPdfUrl());
        if (paper.getKeywords() != null) {
            coreInfo.setKeywords(List.of(paper.getKeywords().split(", ")));
        }
        if (paper.getPaperAuthors() != null) {
            coreInfo.setAuthors(paper.getPaperAuthors().stream()
                    .map(pa -> {
                        var author = pa.getAuthor();
                        String first = author.getFirstname();
                        String last = author.getLastname();
                        if (first == null && last == null) {
                            return "";
                        }
                        if (first == null) {
                            return last;
                        }
                        if (last == null) {
                            return first;
                        }
                        return first + " " + last;
                    })
                    .collect(Collectors.toList()));
        }
        if (paper.getVolume() != null && paper.getVolume().getJournal() != null) {
            coreInfo.setVenue(paper.getVolume().getJournal().getTitle());
        } else if (paper.getConference() != null) {
            coreInfo.setVenue(paper.getConference().getName());
        }
        dto.setCoreInfo(coreInfo);

        return dto;
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
        
        // Abstract
        if (work.abstractInvertedIndex != null) {
            dto.setAbstractText("Available in source");
        }
        
        // Publication date
        if (work.publicationDate != null) {
            dto.setPublicationDate(work.publicationDate);
        }

        // PDF URL
        if (work.primaryLocation != null && work.primaryLocation.pdfUrl != null) {
            dto.setPdfUrl(work.primaryLocation.pdfUrl);
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

        // Keywords from concepts
        if (work.concepts != null) {
            dto.setKeywords(work.concepts.stream()
                .map(concept -> concept.displayName)
                .collect(Collectors.toList()));
            
            // Research fields (top level concepts)
            dto.setResearchFields(work.concepts.stream()
                .filter(c -> c.level == 0 || c.level == 1)
                .map(concept -> {
                    var fieldDto = new com.paperapi.paper_api.dto.ResearchFieldDTO();
                    fieldDto.setFieldName(concept.displayName);
                    fieldDto.setDescription("Level " + concept.level + " research area");
                    return fieldDto;
                }).collect(Collectors.toList()));
        } else {
            dto.setKeywords(Collections.emptyList());
            dto.setResearchFields(Collections.emptyList());
        }

        return dto;
    }

    // --- Lớp nội bộ để hứng JSON từ OpenAlex ---
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class OpenAlexWork {
        public String title;
        @JsonProperty("publication_year") public int publicationYear;
        @JsonProperty("publication_date") public String publicationDate;
        @JsonProperty("cited_by_count") public long citedByCount;
        @JsonProperty("primary_location") public Location primaryLocation;
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
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Source {
        @JsonProperty("display_name") public String displayName;
        public String type;
        @JsonProperty("issn_l") public String issn;
        public String publisher;
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
    
    /**
     * Lấy citations từ Semantic Scholar
     */
    public CitationsResponse getCitationsFromSemanticScholar(String doi) {
        String url = "https://api.semanticscholar.org/graph/v1/paper/DOI:" + doi 
                   + "?fields=title,year,citationCount,citations.title,citations.externalIds,citations.year,citations.authors";
        
        try {
            SemanticScholarPaper response = restTemplate.getForObject(url, SemanticScholarPaper.class);
            
            CitationsResponse citationsResponse = new CitationsResponse();
            if (response != null && response.citations != null) {
                citationsResponse.setCitations(response.citations.stream()
                    .map(c -> {
                        Citation citation = new Citation();
                        citation.setTitle(c.title);
                        citation.setYear(c.year);
                        
                        // Ưu tiên DOI để tạo link trực tiếp đến bài báo gốc
                        if (c.externalIds != null && c.externalIds.doi != null) {
                            citation.setDoi(c.externalIds.doi);
                            citation.setUrl("https://doi.org/" + c.externalIds.doi);
                        } else {
                            // Fallback: Dùng link Semantic Scholar nếu không có DOI
                            citation.setUrl("https://www.semanticscholar.org/paper/" + c.paperId);
                        }
                        
                        if (c.authors != null && !c.authors.isEmpty()) {
                            citation.setAuthors(c.authors.stream()
                                .map(a -> a.name)
                                .collect(Collectors.toList()));
                        }
                        return citation;
                    })
                    .collect(Collectors.toList()));
            } else {
                citationsResponse.setCitations(Collections.emptyList());
            }
            return citationsResponse;
        } catch (Exception e) {
            CitationsResponse empty = new CitationsResponse();
            empty.setCitations(Collections.emptyList());
            return empty;
        }
    }
    
    // DTOs for Semantic Scholar
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class SemanticScholarPaper {
        public String title;
        public Integer year;
        public Long citationCount;
        public List<CitationData> citations;
    }
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class CitationData {
        public String paperId;
        public String title;
        public Integer year;
        public List<AuthorData> authors;
        public ExternalIds externalIds;
    }
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ExternalIds {
        @JsonProperty("DOI") 
        public String doi;
    }
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class AuthorData {
        public String name;
    }
    
    public static class CitationsResponse {
        private List<Citation> citations;
        
        public List<Citation> getCitations() {
            return citations;
        }
        
        public void setCitations(List<Citation> citations) {
            this.citations = citations;
        }
    }
    
    public static class Citation {
        private String title;
        private String url;
        private String doi;
        private Integer year;
        private List<String> authors;
        
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        
        public String getDoi() { return doi; }
        public void setDoi(String doi) { this.doi = doi; }
        
        public Integer getYear() { return year; }
        public void setYear(Integer year) { this.year = year; }
        
        public List<String> getAuthors() { return authors; }
        public void setAuthors(List<String> authors) { this.authors = authors; }
    }
}
