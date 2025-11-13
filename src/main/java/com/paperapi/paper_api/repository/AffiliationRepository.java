package com.paperapi.paper_api.repository;

import com.paperapi.paper_api.entity.Affiliation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AffiliationRepository extends JpaRepository<Affiliation, Long> {
    Optional<Affiliation> findByNameAndCity(String name, String city);
}
