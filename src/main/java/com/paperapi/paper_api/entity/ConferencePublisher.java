package com.paperapi.paper_api.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.io.Serializable;

@Entity
@Table(name = "conference_publisher")
@Data
public class ConferencePublisher {
    
    @EmbeddedId
    private ConferencePublisherId id;

    @ManyToOne
    @MapsId("conferenceId")
    @JoinColumn(name = "conference_id")
    private Conference conference;

    @ManyToOne
    @MapsId("publisherId")
    @JoinColumn(name = "publisher_id")
    private Publisher publisher;

    @Embeddable
    @Data
    public static class ConferencePublisherId implements Serializable {
        @Column(name = "conference_id")
        private Long conferenceId;

        @Column(name = "publisher_id")
        private Long publisherId;
    }
}
