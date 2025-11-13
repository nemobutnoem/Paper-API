package com.paperapi.paper_api.repository;

import com.paperapi.paper_api.entity.ResearchField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ResearchFieldRepository extends JpaRepository<ResearchField, Long> {
    Optional<ResearchField> findByFieldname(String fieldname);
}
