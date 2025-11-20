package com.paperapi.paper_api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.paperapi.paper_api.dto.FilteredPaperDTO;
import com.paperapi.paper_api.dto.PaperResponseDTO;
import com.paperapi.paper_api.entity.Paper;
import com.paperapi.paper_api.repository.PaperRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MetadataService {

    private final PaperRepository paperRepository;
    private final AiAnalysisService aiAnalysisService;

    public MetadataService(PaperRepository paperRepository, AiAnalysisService aiAnalysisService) {
        this.paperRepository = paperRepository;
        this.aiAnalysisService = aiAnalysisService;
    }

    @Transactional(readOnly = true)
    public FilteredPaperDTO filterPaperInfo(Long paperId) throws JsonProcessingException {
        Paper paper = paperRepository.findById(paperId)
                .orElseThrow(() -> new RuntimeException("Paper not found with ID: " + paperId));

        // Create a DTO from the entity
        PaperResponseDTO paperResponseDTO = convertToDTO(paper);

        // Call the AI service to analyze the paper
        return aiAnalysisService.analyzePaper(paperResponseDTO);
    }

    private PaperResponseDTO convertToDTO(Paper paper) {
        // This is a simplified conversion. A real implementation would use a mapper library.
        PaperResponseDTO dto = new PaperResponseDTO();
        dto.setId(paper.getDoi()); // Assuming DOI is the ID in PaperResponseDTO
        dto.setTitle(paper.getTitle());
        dto.setAbstract(paper.getAbstractText());
        dto.setPublicationDate(paper.getPublicationDate());
        // ... copy other relevant fields
        return dto;
    }
}
