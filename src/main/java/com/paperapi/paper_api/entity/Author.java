package com.paperapi.paper_api.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "author")
@Data
public class Author {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "author_id")
    private Long authorId;

    @Column(name = "firstname", length = 100)
    private String firstname;

    @Column(name = "lastname", length = 100)
    private String lastname;

    @Column(name = "orc_id", unique = true, length = 30)
    private String orcId;

    @ManyToOne
    @JoinColumn(name = "affiliation_id")
    private Affiliation affiliation;
}
