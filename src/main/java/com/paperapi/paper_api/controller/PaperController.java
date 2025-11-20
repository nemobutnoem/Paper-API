package com.paperapi.paper_api.controller;

import com.paperapi.paper_api.dto.FilteredPaperDTO;
import com.paperapi.paper_api.dto.PaperResponseDTO;
import com.paperapi.paper_api.entity.Paper;
import com.paperapi.paper_api.service.MetadataAnalyzer;
import com.paperapi.paper_api.service.MetadataService;
import com.paperapi.paper_api.service.PaperPersistenceService;
import com.paperapi.paper_api.service.PaperService;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class PaperController {

	private final PaperService paperService;
	private final PaperPersistenceService paperPersistenceService;
	private final MetadataService metadataService;
	private final MetadataAnalyzer metadataAnalyzer;

	public PaperController(PaperService paperService, PaperPersistenceService paperPersistenceService,
			MetadataService metadataService, MetadataAnalyzer metadataAnalyzer) {
		this.paperService = paperService;
		this.paperPersistenceService = paperPersistenceService;
		this.metadataService = metadataService;
		this.metadataAnalyzer = metadataAnalyzer;
	}

	@GetMapping("/papers")
	public ResponseEntity<PaperResponseDTO> getPaperByDoi(@RequestParam("doi") String doi) {
		PaperResponseDTO dto = paperService.getPaperInfoByDoi(doi);
		if (dto == null) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok(dto);
	}

	@PostMapping("/papers/save")
	@Transactional
	public ResponseEntity<?> savePaperByDoi(@RequestParam("doi") String doi) {
		try {
			// 1. Fetch paper info from OpenAlex
			PaperResponseDTO dto = paperService.getPaperInfoByDoi(doi);
			if (dto == null) {
				return ResponseEntity.notFound().build();
			}

			System.out.println("✅ Step 1: Fetched paper from OpenAlex");

			// 2. Save to database hoặc lấy paper đã tồn tại (Idempotent)
			// Note: Không cần analyze metadata mỗi lần - đã có static metadata từ SQL
			// scripts
			Object[] result = paperPersistenceService.saveOrGetPaper(dto, doi);
			Paper savedPaper = (Paper) result[0];
			boolean isNew = (boolean) result[1];

			if (isNew) {
				System.out.println("✅ Step 2: Saved new paper with ID: " + savedPaper.getPaperId());
			} else {
				System.out.println("✅ Step 2: Paper already exists with ID: " + savedPaper.getPaperId());
			}

			// 3. Filter info using metadata (AI-powered analysis)
			FilteredPaperDTO filtered = metadataService.filterPaperInfo(savedPaper.getPaperId());
			System.out.println("✅ Step 3: AI analysis completed - Quality Score: " +
					(filtered.getQualityAssessment() != null ? filtered.getQualityAssessment().getOverallScore() : 0));

			// Add metadata to response
			Map<String, Object> response = new java.util.HashMap<>();
			response.put("data", filtered);
			response.put("isNew", isNew);
			response.put("message", isNew ? "Paper saved successfully" : "Paper already exists in database");

			return ResponseEntity.ok(response);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			return ResponseEntity.badRequest().body(Map.of(
					"error", "Failed to process AI analysis response.",
					"type", e.getClass().getSimpleName()));
		} catch (Exception e) {
			e.printStackTrace();
			String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
			return ResponseEntity.badRequest().body(Map.of(
					"error", errorMsg,
					"type", e.getClass().getSimpleName()));
		}
	}

	/**
	 * Lấy thông tin filtered của paper đã lưu theo DOI
	 */
	@GetMapping("/papers/filtered")
	@Transactional(readOnly = true)
	public ResponseEntity<?> getFilteredPaper(@RequestParam("doi") String doi) {
		try {
			// Check if paper exists
			if (!paperPersistenceService.isPaperExists(doi)) {
				return ResponseEntity.notFound().build();
			}

			// Get paper and filter
			Paper paper = paperPersistenceService.getPaperByDoi(doi);
			FilteredPaperDTO filtered = metadataService.filterPaperInfo(paper.getPaperId());

			Map<String, Object> response = new java.util.HashMap<>();
			response.put("data", filtered);
			response.put("isNew", false);
			response.put("message", "Paper already exists in database");

			return ResponseEntity.ok(response);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			return ResponseEntity.badRequest().body(Map.of(
					"error", "Failed to process AI analysis response.",
					"type", e.getClass().getSimpleName()));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body("Error: " + e.getMessage());
		}
	}

	/**
	 * Tìm kiếm các bài báo tương tự về mặt ngữ nghĩa
	 */
	@GetMapping("/papers/search/similar")
	public ResponseEntity<?> findSimilarPapers(@RequestParam("query") String query,
			@RequestParam(value = "limit", defaultValue = "10") int limit) {
		try {
			List<FilteredPaperDTO> similarPapers = paperService.findSimilarPapers(query, limit);
			return ResponseEntity.ok(similarPapers);
		} catch (Exception e) {
			e.printStackTrace();
			return ResponseEntity.badRequest().body("Error: " + e.getMessage());
		}
	}

	/**
	 * Lấy citations từ Semantic Scholar
	 */
	@GetMapping("/papers/citations")
	public ResponseEntity<?> getCitations(@RequestParam("doi") String doi) {
		try {
			return ResponseEntity.ok(paperService.getCitationsFromSemanticScholar(doi));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body("Error: " + e.getMessage());
		}
	}

	/**
	 * Lấy metadata schema của một table
	 */
	@GetMapping("/metadata/table")
	public ResponseEntity<?> getTableMetadata(@RequestParam("tableName") String tableName) {
		try {
			return ResponseEntity.ok(metadataAnalyzer.getTableMetadata(tableName));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body("Error: " + e.getMessage());
		}
	}

	/**
	 * Kiểm tra paper đã được lưu chưa
	 */
	@GetMapping("/papers/exists")
	public ResponseEntity<?> checkPaperExists(@RequestParam("doi") String doi) {
		try {
			boolean exists = paperPersistenceService.isPaperExists(doi);
			return ResponseEntity.ok(Map.of(
					"exists", exists,
					"doi", doi));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body("Error: " + e.getMessage());
		}
	}
}
