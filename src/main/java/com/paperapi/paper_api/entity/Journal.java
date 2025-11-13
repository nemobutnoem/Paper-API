package com.paperapi.paper_api.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "journal")
@Data
public class Journal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "journal_id")
    private Long journalId;

    @Column(name = "title", nullable = false, columnDefinition = "TEXT")
    private String title;

    @Column(name = "issn", unique = true, length = 20)
    private String issn;

    @Column(name = "impactfactor")
    private Double impactFactor;

    @ManyToOne
    @JoinColumn(name = "publisher_id")
    private Publisher publisher;
}
