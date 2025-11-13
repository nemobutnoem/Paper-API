package com.paperapi.paper_api.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.io.Serializable;

@Entity
@Table(name = "paper_field")
@Data
public class PaperField {
    
    @EmbeddedId
    private PaperFieldId id;

    @ManyToOne
    @MapsId("paperId")
    @JoinColumn(name = "paper_id")
    private Paper paper;

    @ManyToOne
    @MapsId("fieldId")
    @JoinColumn(name = "field_id")
    private ResearchField researchField;

    @Embeddable
    @Data
    public static class PaperFieldId implements Serializable {
        @Column(name = "paper_id")
        private Long paperId;

        @Column(name = "field_id")
        private Long fieldId;
    }
}
