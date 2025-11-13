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
    private List<String> keywords;
    private JournalDTO journal;
    private ConferenceDTO conference;
    private VolumeDTO volume;
    private List<ResearchFieldDTO> researchFields;
}