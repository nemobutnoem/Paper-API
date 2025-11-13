package com.paperapi.paper_api.dto;

import lombok.Data;

@Data
public class VolumeDTO {
    private String volumeNumber;
    private String issueNumber;
    private Integer publicationYear;
}
