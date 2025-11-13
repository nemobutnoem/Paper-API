package com.paperapi.paper_api.dto;

import lombok.Data;

@Data
public class ConferenceDTO {
    private String name;
    private String acronym;
    private String city;
    private Integer year;
}
