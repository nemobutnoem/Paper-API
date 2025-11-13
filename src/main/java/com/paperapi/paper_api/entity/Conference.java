package com.paperapi.paper_api.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "conference")
@Data
public class Conference {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "conference_id")
    private Long conferenceId;

    @Column(name = "name", columnDefinition = "TEXT")
    private String name;

    @Column(name = "acronym", length = 50)
    private String acronym;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "year")
    private Integer year;
}
