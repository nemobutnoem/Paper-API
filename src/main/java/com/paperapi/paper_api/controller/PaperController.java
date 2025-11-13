package com.paperapi.paper_api.controller;

import com.paperapi.paper_api.dto.PaperResponseDTO;
import com.paperapi.paper_api.service.PaperService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api")
public class PaperController {

    private final PaperService paperService;

    public PaperController(PaperService paperService) {
        this.paperService = paperService;
    }

    @GetMapping("/papers")
    public ResponseEntity<PaperResponseDTO> getPaperByDoi(@RequestParam("doi") String doi) {
        PaperResponseDTO dto = paperService.getPaperInfoByDoi(doi);
        if (dto == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(dto);
    }
}