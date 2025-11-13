package com.paperapi.paper_api.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.io.Serializable;

@Entity
@Table(name = "paper_author")
@Data
public class PaperAuthor {
    
    @EmbeddedId
    private PaperAuthorId id;

    @ManyToOne
    @MapsId("paperId")
    @JoinColumn(name = "paper_id")
    private Paper paper;

    @ManyToOne
    @MapsId("authorId")
    @JoinColumn(name = "author_id")
    private Author author;

    @Column(name = "author_order")
    private Integer authorOrder;

    @Embeddable
    @Data
    public static class PaperAuthorId implements Serializable {
        @Column(name = "paper_id")
        private Long paperId;

        @Column(name = "author_id")
        private Long authorId;
    }
}
