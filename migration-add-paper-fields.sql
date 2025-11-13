-- ============================================================
-- Migration: Thêm các cột còn thiếu vào bảng paper
-- Date: 2025-11-13
-- Description: Thêm citation_count, pdf_url, keywords
-- ============================================================

-- 1. Thêm các cột mới vào bảng paper
ALTER TABLE public.paper 
ADD COLUMN IF NOT EXISTS citation_count BIGINT,
ADD COLUMN IF NOT EXISTS pdf_url TEXT,
ADD COLUMN IF NOT EXISTS keywords TEXT;

-- 2. Thêm comment cho các cột mới
COMMENT ON COLUMN public.paper.citation_count IS 'Số lần bài báo được trích dẫn bởi các công trình khác. Phản ánh tầm ảnh hưởng của bài báo.';
COMMENT ON COLUMN public.paper.pdf_url IS 'Đường dẫn URL tới file PDF toàn văn của bài báo (nếu có).';
COMMENT ON COLUMN public.paper.keywords IS 'Các từ khóa chính của bài báo, phân tách bằng dấu phẩy. Dùng cho tìm kiếm và phân loại.';

-- 3. Cập nhật metadata.columns_info (nếu bạn có bảng metadata)
INSERT INTO metadata.columns_info (table_id, column_name, data_type, description)
SELECT 
    (SELECT table_id FROM metadata.tables_info WHERE table_name = 'paper'),
    'citation_count',
    'BIGINT',
    'Số lần bài báo được trích dẫn bởi các công trình khác. Phản ánh tầm ảnh hưởng của bài báo.'
WHERE NOT EXISTS (
    SELECT 1 FROM metadata.columns_info 
    WHERE column_name = 'citation_count' 
    AND table_id = (SELECT table_id FROM metadata.tables_info WHERE table_name = 'paper')
);

INSERT INTO metadata.columns_info (table_id, column_name, data_type, description)
SELECT 
    (SELECT table_id FROM metadata.tables_info WHERE table_name = 'paper'),
    'pdf_url',
    'TEXT',
    'Đường dẫn URL tới file PDF toàn văn của bài báo (nếu có).'
WHERE NOT EXISTS (
    SELECT 1 FROM metadata.columns_info 
    WHERE column_name = 'pdf_url' 
    AND table_id = (SELECT table_id FROM metadata.tables_info WHERE table_name = 'paper')
);

INSERT INTO metadata.columns_info (table_id, column_name, data_type, description)
SELECT 
    (SELECT table_id FROM metadata.tables_info WHERE table_name = 'paper'),
    'keywords',
    'TEXT',
    'Các từ khóa chính của bài báo, phân tách bằng dấu phẩy. Dùng cho tìm kiếm và phân loại.'
WHERE NOT EXISTS (
    SELECT 1 FROM metadata.columns_info 
    WHERE column_name = 'keywords' 
    AND table_id = (SELECT table_id FROM metadata.tables_info WHERE table_name = 'paper')
);

-- 4. Verify migration
SELECT 
    column_name, 
    data_type, 
    is_nullable,
    column_default
FROM information_schema.columns
WHERE table_schema = 'public' 
AND table_name = 'paper'
AND column_name IN ('citation_count', 'pdf_url', 'keywords')
ORDER BY column_name;

-- Success message
DO $$
BEGIN
    RAISE NOTICE '✅ Migration completed successfully!';
    RAISE NOTICE 'Added columns: citation_count, pdf_url, keywords to public.paper';
END $$;
