package com.paperapi.paper_api.repository;

import com.paperapi.paper_api.entity.Paper;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaperRepository extends JpaRepository<Paper, Long> {
    Optional<Paper> findByDoi(String doi);

    @Query("SELECT DISTINCT p FROM Paper p " +
            "LEFT JOIN FETCH p.paperAuthors pa " +
            "LEFT JOIN FETCH pa.author " +
            "LEFT JOIN FETCH p.paperFields pf " +
            "LEFT JOIN FETCH pf.researchField " +
            "LEFT JOIN FETCH p.volume v " +
            "LEFT JOIN FETCH v.journal " +
            "LEFT JOIN FETCH p.conference " +
            "WHERE p.doi = :doi")
    Optional<Paper> findByDoiWithRelations(@Param("doi") String doi);
}
