package com.paperapi.paper_api.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "research_field")
@Data
public class ResearchField {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "field_id")
    private Long fieldId;

    @Column(name = "fieldname", nullable = false)
    private String fieldname;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
}
