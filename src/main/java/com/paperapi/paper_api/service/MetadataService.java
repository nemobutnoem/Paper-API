package com.paperapi.paper_api.service;

import com.paperapi.paper_api.dto.FilteredPaperDTO;
import com.paperapi.paper_api.entity.*;
import com.paperapi.paper_api.repository.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MetadataService {

    private final JdbcTemplate jdbcTemplate;
    private final PaperRepository paperRepository;

    public MetadataService(JdbcTemplate jdbcTemplate, PaperRepository paperRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.paperRepository = paperRepository;
    }

    /**
     * Lọc thông tin bài báo dựa trên metadata
     * CHỈ trả về các thông tin QUAN TRỌNG được đánh dấu trong metadata
     */
    public FilteredPaperDTO filterPaperInfo(Long paperId) {
        // 1. Lấy paper từ database
        Paper paper = paperRepository.findById(paperId)
                .orElseThrow(() -> new RuntimeException("Paper not found with ID: " + paperId));

        FilteredPaperDTO filtered = new FilteredPaperDTO();
        
        // 2. Lấy description từ metadata.tables_info
        String tableDesc = getTableDescription("paper");
        filtered.setTableDescription(tableDesc);

        // 3. LỌC CHỈ CÁC TRƯỜNG QUAN TRỌNG từ metadata
        List<FilteredPaperDTO.ColumnInfo> importantFields = getImportantColumnsOnly(paper);
        filtered.setImportantFields(importantFields);

        // 4. Thông tin cơ bản (luôn hiển thị)
        filtered.setPaperId(paper.getPaperId());
        filtered.setTitle(paper.getTitle());
        filtered.setDoi(paper.getDoi());
        filtered.setCitationCount(paper.getCitationCount());

        // 5. Lấy danh sách authors (tối đa 3 tác giả đầu)
        List<String> authors = paper.getPaperAuthors().stream()
                .sorted((a, b) -> Integer.compare(a.getAuthorOrder(), b.getAuthorOrder()))
                .limit(3) // CHỈ lấy 3 tác giả đầu
                .map(pa -> pa.getAuthor().getFirstname() + " " + pa.getAuthor().getLastname())
                .collect(Collectors.toList());
        
        if (paper.getPaperAuthors().size() > 3) {
            authors.add("et al."); // Thêm "et al." nếu có nhiều hơn 3 tác giả
        }
        filtered.setAuthors(authors);

        // 6. Lấy top 3 research fields
        List<String> fields = paper.getPaperFields().stream()
                .limit(3) // CHỈ lấy 3 lĩnh vực đầu
                .map(pf -> pf.getResearchField().getFieldname())
                .collect(Collectors.toList());
        filtered.setResearchFields(fields);

        // 7. Parse keywords (tối đa 5 keywords)
        if (paper.getKeywords() != null) {
            List<String> keywords = List.of(paper.getKeywords().split(",\\s*"));
            filtered.setKeywords(keywords.stream().limit(5).collect(Collectors.toList()));
        }

        // 8. Publication venue
        if (paper.getVolume() != null && paper.getVolume().getJournal() != null) {
            filtered.setPublicationVenue(paper.getVolume().getJournal().getTitle());
            filtered.setPublicationYear(paper.getVolume().getPublicationYear());
        } else if (paper.getConference() != null) {
            filtered.setPublicationVenue(paper.getConference().getName());
            filtered.setPublicationYear(paper.getConference().getYear());
        }
        
        // 9. Năm xuất bản
        filtered.setPublicationDate(paper.getPublicationDate());

        return filtered;
    }

    /**
     * Lấy description của bảng từ metadata
     */
    private String getTableDescription(String tableName) {
        try {
            String sql = "SELECT description FROM metadata.tables_info WHERE table_name = ?";
            return jdbcTemplate.queryForObject(sql, String.class, tableName);
        } catch (Exception e) {
            return "No description available";
        }
    }

    /**
     * LỌC CHỈ CÁC TRƯỜNG được đánh dấu là QUAN TRỌNG trong metadata
     * Dựa vào cột 'is_important' hoặc description chứa keyword "quan trọng"
     */
    private List<FilteredPaperDTO.ColumnInfo> getImportantColumnsOnly(Paper paper) {
        List<FilteredPaperDTO.ColumnInfo> columns = new ArrayList<>();

        // Lấy CHỈ các cột được đánh dấu quan trọng trong metadata
        String sql = """
            SELECT c.column_name, c.description, c.data_type
            FROM metadata.columns_info c
            JOIN metadata.tables_info t ON c.table_id = t.table_id
            WHERE t.table_name = 'paper' 
            AND c.description IS NOT NULL
            AND (
                c.column_name IN ('title', 'citation_count', 'publication_date')
                OR c.description ILIKE '%quan trọng%'
            )
            ORDER BY 
                CASE 
                    WHEN c.column_name = 'title' THEN 1
                    WHEN c.column_name = 'citation_count' THEN 2
                    WHEN c.column_name = 'publication_date' THEN 3
                    ELSE 4
                END
            LIMIT 5
        """;

        try {
            List<Map<String, Object>> metadataList = jdbcTemplate.queryForList(sql);

            for (Map<String, Object> meta : metadataList) {
                FilteredPaperDTO.ColumnInfo col = new FilteredPaperDTO.ColumnInfo();
                String columnName = (String) meta.get("column_name");
                col.setColumnName(columnName);
                col.setDescription((String) meta.get("description"));

                // Lấy giá trị tương ứng (CHỈ lấy giá trị ngắn gọn)
                switch (columnName) {
                    case "title" -> col.setValue(paper.getTitle());
                    case "doi" -> col.setValue(paper.getDoi());
                    case "publication_date" -> col.setValue(paper.getPublicationDate());
                    case "citation_count" -> col.setValue(paper.getCitationCount());
                    case "keywords" -> col.setValue(paper.getKeywords() != null ? 
                        paper.getKeywords().split(",")[0] + "..." : "N/A"); // Chỉ lấy keyword đầu
                }

                columns.add(col);
            }
        } catch (Exception e) {
            // Nếu không có metadata, trả về empty list
        }

        return columns;
    }
}
