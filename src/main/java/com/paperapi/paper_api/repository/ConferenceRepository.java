package com.paperapi.paper_api.repository;

import com.paperapi.paper_api.entity.Conference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConferenceRepository extends JpaRepository<Conference, Long> {
    Optional<Conference> findByNameAndYear(String name, Integer year);
}
