package com.paperapi.paper_api.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "affiliation")
@Data
public class Affiliation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "affiliation_id")
    private Long affiliationId;

    @Column(name = "name")
    private String name;

    @Column(name = "city")
    private String city;

    @Column(name = "country")
    private String country;
}
