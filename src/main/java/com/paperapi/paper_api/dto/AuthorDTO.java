package com.paperapi.paper_api.dto;

import lombok.Data;

@Data
public class AuthorDTO {
    private String firstName;
    private String lastName;
    private String orcId;
    private AffiliationDTO affiliation;
}
