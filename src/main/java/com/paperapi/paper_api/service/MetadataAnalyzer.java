package com.paperapi.paper_api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Service để phân tích JSON từ Paper API và tự động tạo metadata
 */
@Service
public class MetadataAnalyzer {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public MetadataAnalyzer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Phân tích JSON từ Paper API và cập nhật metadata schema
     */
    @Transactional
    public Map<String, Object> analyzeAndUpdateMetadata(Object jsonData, String tableName) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 1. Convert object to JSON
            String jsonString = objectMapper.writeValueAsString(jsonData);
            JsonNode rootNode = objectMapper.readTree(jsonString);
            
            // 2. Phân tích cấu trúc JSON
            Map<String, String> columns = analyzeJsonStructure(rootNode);
            
            // 3. Kiểm tra table đã tồn tại chưa
            Long tableId = getOrCreateTable(tableName, "Tự động tạo từ Paper API JSON");
            
            // 4. Cập nhật metadata columns
            updateColumnsMetadata(tableId, tableName, columns);
            
            result.put("success", true);
            result.put("tableName", tableName);
            result.put("tableId", tableId);
            result.put("columnsAnalyzed", columns.size());
            result.put("columns", columns);
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }

    /**
     * Phân tích cấu trúc JSON để xác định kiểu dữ liệu
     */
    private Map<String, String> analyzeJsonStructure(JsonNode node) {
        Map<String, String> columns = new LinkedHashMap<>();
        
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String fieldName = field.getKey();
            JsonNode value = field.getValue();
            
            String dataType = inferDataType(value);
            columns.put(fieldName, dataType);
        }
        
        return columns;
    }

    /**
     * Suy luận kiểu dữ liệu từ JsonNode
     */
    private String inferDataType(JsonNode node) {
        if (node.isNull()) {
            return "TEXT";
        } else if (node.isInt()) {
            return "INTEGER";
        } else if (node.isLong()) {
            return "BIGINT";
        } else if (node.isFloat() || node.isDouble()) {
            return "FLOAT";
        } else if (node.isBoolean()) {
            return "BOOLEAN";
        } else if (node.isArray()) {
            return "TEXT"; // Lưu array dạng JSON string
        } else if (node.isObject()) {
            return "TEXT"; // Lưu nested object dạng JSON string
        } else if (node.isTextual()) {
            String text = node.asText();
            if (text.length() > 500) {
                return "TEXT";
            } else if (text.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return "DATE";
            } else {
                return "VARCHAR(500)";
            }
        }
        return "TEXT";
    }

    /**
     * Lấy hoặc tạo mới table trong metadata.tables_info
     */
    private Long getOrCreateTable(String tableName, String description) {
        // Kiểm tra table đã tồn tại
        String checkSql = "SELECT table_id FROM metadata.tables_info WHERE table_name = ?";
        List<Long> existing = jdbcTemplate.query(checkSql, 
            (rs, rowNum) -> rs.getLong("table_id"), 
            tableName);
        
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        
        // Tạo mới
        String insertSql = "INSERT INTO metadata.tables_info (table_name, description) VALUES (?, ?) RETURNING table_id";
        return jdbcTemplate.queryForObject(insertSql, Long.class, tableName, description);
    }

    /**
     * Cập nhật metadata cho các cột
     */
    private void updateColumnsMetadata(Long tableId, String tableName, Map<String, String> columns) {
        for (Map.Entry<String, String> column : columns.entrySet()) {
            String columnName = column.getKey();
            String dataType = column.getValue();
            
            // Kiểm tra column đã tồn tại chưa
            String checkSql = "SELECT COUNT(*) FROM metadata.columns_info WHERE table_id = ? AND column_name = ?";
            Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class, tableId, columnName);
            
            if (count == 0) {
                // Tạo mới
                String insertSql = """
                    INSERT INTO metadata.columns_info (table_id, column_name, data_type, description)
                    VALUES (?, ?, ?, ?)
                """;
                
                String description = generateColumnDescription(columnName, dataType);
                jdbcTemplate.update(insertSql, tableId, columnName, dataType, description);
            }
        }
    }

    /**
     * Tạo description tự động cho column
     */
    private String generateColumnDescription(String columnName, String dataType) {
        Map<String, String> descriptions = Map.ofEntries(
            Map.entry("title", "Tiêu đề của bài báo khoa học"),
            Map.entry("doi", "Digital Object Identifier - Mã định danh duy nhất"),
            Map.entry("authors", "Danh sách tác giả"),
            Map.entry("publicationYear", "Năm xuất bản"),
            Map.entry("citationCount", "Số lượng trích dẫn"),
            Map.entry("abstract", "Tóm tắt nội dung bài báo"),
            Map.entry("keywords", "Từ khóa chính"),
            Map.entry("journal", "Tạp chí xuất bản"),
            Map.entry("pdfUrl", "Link tới file PDF")
        );
        
        return descriptions.getOrDefault(columnName, 
            "Trường " + columnName + " (Kiểu: " + dataType + ")");
    }

    /**
     * Lấy metadata summary của một table
     */
    public Map<String, Object> getTableMetadata(String tableName) {
        Map<String, Object> metadata = new HashMap<>();
        
        try {
            // Lấy table info
            String tableSql = "SELECT * FROM metadata.tables_info WHERE table_name = ?";
            List<Map<String, Object>> tableInfo = jdbcTemplate.queryForList(tableSql, tableName);
            
            if (tableInfo.isEmpty()) {
                metadata.put("exists", false);
                return metadata;
            }
            
            metadata.put("exists", true);
            metadata.put("table", tableInfo.get(0));
            
            // Lấy columns info
            Long tableId = ((Number) tableInfo.get(0).get("table_id")).longValue();
            String columnsSql = "SELECT * FROM metadata.columns_info WHERE table_id = ? ORDER BY column_id";
            List<Map<String, Object>> columns = jdbcTemplate.queryForList(columnsSql, tableId);
            
            metadata.put("columns", columns);
            metadata.put("columnCount", columns.size());
            
        } catch (Exception e) {
            metadata.put("error", e.getMessage());
        }
        
        return metadata;
    }
}
