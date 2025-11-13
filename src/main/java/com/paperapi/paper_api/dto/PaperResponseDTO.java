package com.paperapi.paper_api.dto;

import lombok.Data;
import java.util.List;

@Data
public class PaperResponseDTO {
    private String title;
    private String abstractText;
    private String publicationDate;
    private List<AuthorDTO> authors;
    private int publicationYear;
    private long citationCount;
    private String pdfUrl;
    private String doi;
    private String landingPageUrl;
    private Boolean isOpenAccess;
    private List<String> keywords;
    // New: richer content for quick reading in extension
    private List<String> highlights; // 3-5 câu nổi bật từ abstract
    private String contentPreview;   // Đoạn trích ngắn (từ abstract hoặc nội dung)
    private JournalDTO journal;
    private ConferenceDTO conference;
    private VolumeDTO volume;
    private List<ResearchFieldDTO> researchFields;
}