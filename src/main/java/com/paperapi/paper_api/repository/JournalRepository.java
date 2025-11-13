package com.paperapi.paper_api.repository;

import com.paperapi.paper_api.entity.Journal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface JournalRepository extends JpaRepository<Journal, Long> {
    Optional<Journal> findByIssn(String issn);
    Optional<Journal> findByTitle(String title);
}
