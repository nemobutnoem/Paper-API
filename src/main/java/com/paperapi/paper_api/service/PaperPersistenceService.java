package com.paperapi.paper_api.service;

import com.paperapi.paper_api.dto.*;
import com.paperapi.paper_api.entity.*;
import com.paperapi.paper_api.repository.*;
import com.pgvector.PGvector;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class PaperPersistenceService {

    private final PaperRepository paperRepository;
    private final AuthorRepository authorRepository;
    private final AffiliationRepository affiliationRepository;
    private final PublisherRepository publisherRepository;
    private final JournalRepository journalRepository;
    private final VolumeRepository volumeRepository;
    private final ConferenceRepository conferenceRepository;
    private final ResearchFieldRepository researchFieldRepository;
    private final PaperAuthorRepository paperAuthorRepository;
    private final PaperFieldRepository paperFieldRepository;
    private final VectorService vectorService;

    public PaperPersistenceService(
            PaperRepository paperRepository,
            AuthorRepository authorRepository,
            AffiliationRepository affiliationRepository,
            PublisherRepository publisherRepository,
            JournalRepository journalRepository,
            VolumeRepository volumeRepository,
            ConferenceRepository conferenceRepository,
            ResearchFieldRepository researchFieldRepository,
            PaperAuthorRepository paperAuthorRepository,
            PaperFieldRepository paperFieldRepository,
            VectorService vectorService) {
        this.paperRepository = paperRepository;
        this.authorRepository = authorRepository;
        this.affiliationRepository = affiliationRepository;
        this.publisherRepository = publisherRepository;
        this.journalRepository = journalRepository;
        this.volumeRepository = volumeRepository;
        this.conferenceRepository = conferenceRepository;
        this.researchFieldRepository = researchFieldRepository;
        this.paperAuthorRepository = paperAuthorRepository;
        this.paperFieldRepository = paperFieldRepository;
        this.vectorService = vectorService;
    }

    /**
     * Lấy paper từ database theo DOI với tất cả relationships
     */
    @Transactional(readOnly = true)
    public Paper getPaperByDoi(String doi) {
        return paperRepository.findByDoiWithRelations(doi)
                .orElseThrow(() -> new RuntimeException("Paper with DOI " + doi + " not found"));
    }

    /**
     * Save paper if not exists, return existing paper if already saved
     * 
     * @return Object[] {Paper, boolean isNew}
     */
    @Transactional
    public Object[] saveOrGetPaper(PaperResponseDTO dto, String doi) {
        // Check if paper already exists - use eager fetch to load all relationships
        var existingPaper = paperRepository.findByDoiWithRelations(doi);
        if (existingPaper.isPresent()) {
            return new Object[] { existingPaper.get(), false };
        }

        // Create and save paper
        Paper paper = new Paper();
        paper.setTitle(dto.getTitle());
        paper.setAbstractText(dto.getAbstractText());
        paper.setDoi(doi);
        paper.setPublicationDate(dto.getPublicationDate());
        paper.setCitationCount(dto.getCitationCount());
        paper.setPdfUrl(dto.getPdfUrl());

        // Convert keywords list to comma-separated string
        if (dto.getKeywords() != null && !dto.getKeywords().isEmpty()) {
            paper.setKeywords(String.join(", ", dto.getKeywords()));
        }

        // Generate and set embedding
        String textToEmbed = dto.getTitle() + " " + dto.getAbstractText();
        List<Double> embedding = vectorService.getEmbedding(textToEmbed);
        if (embedding != null && !embedding.isEmpty()) {
            float[] floatArray = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                floatArray[i] = embedding.get(i).floatValue();
            }
            paper.setEmbedding(new PGvector(floatArray));
        }

        // Handle Journal and Volume
        if (dto.getJournal() != null) {
            Journal journal = saveOrGetJournal(dto.getJournal());

            if (dto.getVolume() != null) {
                Volume volume = new Volume();
                volume.setVolumeNumber(dto.getVolume().getVolumeNumber());
                volume.setIssueNumber(dto.getVolume().getIssueNumber());
                volume.setPublicationYear(dto.getVolume().getPublicationYear());
                volume.setJournal(journal);
                volume = volumeRepository.save(volume);
                paper.setVolume(volume);
            }
        }

        // Handle Conference
        if (dto.getConference() != null) {
            Conference conference = saveOrGetConference(dto.getConference());
            paper.setConference(conference);
        }

        // Save paper first to get ID
        paper = paperRepository.save(paper);

        // Handle Authors
        if (dto.getAuthors() != null) {
            int order = 1;
            for (AuthorDTO authorDTO : dto.getAuthors()) {
                Author author = saveOrGetAuthor(authorDTO);

                PaperAuthor paperAuthor = new PaperAuthor();
                PaperAuthor.PaperAuthorId id = new PaperAuthor.PaperAuthorId();
                id.setPaperId(paper.getPaperId());
                id.setAuthorId(author.getAuthorId());
                paperAuthor.setId(id);
                paperAuthor.setPaper(paper);
                paperAuthor.setAuthor(author);
                paperAuthor.setAuthorOrder(order++);

                paperAuthorRepository.save(paperAuthor);
            }
        }

        // Handle Research Fields
        if (dto.getResearchFields() != null) {
            for (ResearchFieldDTO fieldDTO : dto.getResearchFields()) {
                ResearchField field = saveOrGetResearchField(fieldDTO);

                PaperField paperField = new PaperField();
                PaperField.PaperFieldId id = new PaperField.PaperFieldId();
                id.setPaperId(paper.getPaperId());
                id.setFieldId(field.getFieldId());
                paperField.setId(id);
                paperField.setPaper(paper);
                paperField.setResearchField(field);

                paperFieldRepository.save(paperField);
            }
        }

        return new Object[] { paper, true };
    }

    /**
     * Check if a paper with given DOI exists
     */
    public boolean isPaperExists(String doi) {
        return paperRepository.findByDoi(doi).isPresent();
    }

    private Author saveOrGetAuthor(AuthorDTO dto) {
        // Try to find by ORCID if available
        if (dto.getOrcId() != null && !dto.getOrcId().isEmpty()) {
            var existing = authorRepository.findByOrcId(dto.getOrcId());
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        Author author = new Author();
        author.setFirstname(dto.getFirstName());
        author.setLastname(dto.getLastName());
        author.setOrcId(dto.getOrcId());

        // Handle Affiliation
        if (dto.getAffiliation() != null) {
            Affiliation affiliation = saveOrGetAffiliation(dto.getAffiliation());
            author.setAffiliation(affiliation);
        }

        return authorRepository.save(author);
    }

    private Affiliation saveOrGetAffiliation(AffiliationDTO dto) {
        var existing = affiliationRepository.findByNameAndCity(dto.getName(), dto.getCity());
        if (existing.isPresent()) {
            return existing.get();
        }

        Affiliation affiliation = new Affiliation();
        affiliation.setName(dto.getName());
        affiliation.setCity(dto.getCity());
        affiliation.setCountry(dto.getCountry());

        return affiliationRepository.save(affiliation);
    }

    private Journal saveOrGetJournal(JournalDTO dto) {
        // Try to find by ISSN
        if (dto.getIssn() != null && !dto.getIssn().isEmpty()) {
            var existing = journalRepository.findByIssn(dto.getIssn());
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        Journal journal = new Journal();
        journal.setTitle(dto.getTitle());
        journal.setIssn(dto.getIssn());
        journal.setImpactFactor(dto.getImpactFactor());

        // Handle Publisher
        if (dto.getPublisher() != null) {
            Publisher publisher = saveOrGetPublisher(dto.getPublisher());
            journal.setPublisher(publisher);
        }

        return journalRepository.save(journal);
    }

    private Publisher saveOrGetPublisher(PublisherDTO dto) {
        var existing = publisherRepository.findByName(dto.getName());
        if (existing.isPresent()) {
            return existing.get();
        }

        Publisher publisher = new Publisher();
        publisher.setName(dto.getName());
        publisher.setWebsite(dto.getWebsite());
        publisher.setCountry(dto.getCountry());

        return publisherRepository.save(publisher);
    }

    private Conference saveOrGetConference(ConferenceDTO dto) {
        var existing = conferenceRepository.findByNameAndYear(dto.getName(), dto.getYear());
        if (existing.isPresent()) {
            return existing.get();
        }

        Conference conference = new Conference();
        conference.setName(dto.getName());
        conference.setAcronym(dto.getAcronym());
        conference.setCity(dto.getCity());
        conference.setYear(dto.getYear());

        return conferenceRepository.save(conference);
    }

    private ResearchField saveOrGetResearchField(ResearchFieldDTO dto) {
        var existing = researchFieldRepository.findByFieldname(dto.getFieldName());
        if (existing.isPresent()) {
            return existing.get();
        }

        ResearchField field = new ResearchField();
        field.setFieldname(dto.getFieldName());
        field.setDescription(dto.getDescription());

        return researchFieldRepository.save(field);
    }
}
