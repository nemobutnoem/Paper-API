package com.paperapi.paper_api.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "paper")
@Data
public class Paper {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "paper_id")
    private Long paperId;

    @Column(name = "title", nullable = false, columnDefinition = "TEXT")
    private String title;

    @Column(name = "abstract", columnDefinition = "TEXT")
    private String abstractText;

    @Column(name = "publication_date")
    private String publicationDate;

    @Column(name = "doi", unique = true)
    private String doi;

    @Column(name = "citation_count")
    private Long citationCount;

    @Column(name = "pdf_url")
    private String pdfUrl;

    @Column(name = "keywords", columnDefinition = "TEXT")
    private String keywords;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conference_id")
    @ToString.Exclude
    private Conference conference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "volume_id")
    @ToString.Exclude
    private Volume volume;

    @OneToMany(mappedBy = "paper", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @ToString.Exclude
    private Set<PaperAuthor> paperAuthors = new HashSet<>();

    @OneToMany(mappedBy = "paper", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @ToString.Exclude
    private Set<PaperField> paperFields = new HashSet<>();
}
