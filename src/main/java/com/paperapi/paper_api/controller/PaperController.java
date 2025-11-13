package com.paperapi.paper_api.controller;

import com.paperapi.paper_api.dto.FilteredPaperDTO;
import com.paperapi.paper_api.dto.PaperResponseDTO;
import com.paperapi.paper_api.entity.Paper;
import com.paperapi.paper_api.service.MetadataAnalyzer;
import com.paperapi.paper_api.service.MetadataService;
import com.paperapi.paper_api.service.PaperPersistenceService;
import com.paperapi.paper_api.service.PaperService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
	public ResponseEntity<?> savePaperByDoi(@RequestParam("doi") String doi) {
		try {
			// 1. Fetch paper info from OpenAlex
			PaperResponseDTO dto = paperService.getPaperInfoByDoi(doi);
			if (dto == null) {
				return ResponseEntity.notFound().build();
			}

			System.out.println("✅ Step 1: Fetched paper from OpenAlex");

			// 2. Phân tích JSON và cập nhật metadata schema
			Map<String, Object> metadataResult = metadataAnalyzer.analyzeAndUpdateMetadata(dto, "paper");
			System.out.println("✅ Step 2: Metadata analysis result: " + metadataResult);

			// 3. Save to database hoặc lấy paper đã tồn tại
			Paper savedPaper;
			try {
				savedPaper = paperPersistenceService.savePaper(dto, doi);
				System.out.println("✅ Step 3: Saved new paper with ID: " + savedPaper.getPaperId());
			} catch (RuntimeException e) {
				// Nếu paper đã tồn tại, lấy từ database
				if (e.getMessage() != null && e.getMessage().contains("already exists")) {
					savedPaper = paperPersistenceService.getPaperByDoi(doi);
					System.out.println("✅ Step 3: Retrieved existing paper with ID: " + savedPaper.getPaperId());
				} else {
					throw e;
				}
			}
			
			// 4. Filter info using metadata
			FilteredPaperDTO filtered = metadataService.filterPaperInfo(savedPaper.getPaperId());
			System.out.println("✅ Step 4: Filtered paper info with " + 
				(filtered.getImportantFields() != null ? filtered.getImportantFields().size() : 0) + " important fields");
			
			return ResponseEntity.ok(filtered);
		} catch (Exception e) {
			e.printStackTrace();
			String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
			return ResponseEntity.badRequest().body(Map.of(
				"error", errorMsg,
				"type", e.getClass().getSimpleName()
			));
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
}
