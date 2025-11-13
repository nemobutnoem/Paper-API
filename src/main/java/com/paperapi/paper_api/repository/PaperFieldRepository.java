package com.paperapi.paper_api.repository;

import com.paperapi.paper_api.entity.PaperField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaperFieldRepository extends JpaRepository<PaperField, PaperField.PaperFieldId> {
}
