package com.paperapi.paper_api.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "volume")
@Data
public class Volume {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "volume_id")
    private Long volumeId;

    @Column(name = "volumenumber")
    private String volumeNumber;

    @Column(name = "issuenumber")
    private String issueNumber;

    @Column(name = "publicationyear")
    private Integer publicationYear;

    @ManyToOne
    @JoinColumn(name = "journal_id")
    private Journal journal;
}
