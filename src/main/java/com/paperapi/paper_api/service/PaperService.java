package com.paperapi.paper_api.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.paperapi.paper_api.dto.PaperResponseDTO;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
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
}
