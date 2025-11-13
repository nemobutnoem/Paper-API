package com.paperapi.paper_api.repository;

import com.paperapi.paper_api.entity.PaperAuthor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaperAuthorRepository extends JpaRepository<PaperAuthor, PaperAuthor.PaperAuthorId> {
}
