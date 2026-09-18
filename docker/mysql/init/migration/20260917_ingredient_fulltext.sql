-- ---------------------------------------------------------------------------
-- 2026-09-17  食材搜索降级方案升级：LIKE '%x%' → ngram 全文索引
--
-- 问题：原降级实现用 `WHERE name LIKE '%番%' AND name LIKE '%茄%'`，
--       前导通配符导致 B+ 树索引完全失效，只能全表扫描；且无相关度排序。
--
-- 方案：MySQL 5.7.6+ 内置 ngram 全文解析器（专为中日韩设计），
--       建立真正的倒排索引，检索走索引而非全表扫描，并支持 MATCH 相关度评分排序。
--
-- 前置条件：
--   1. MySQL >= 5.7.6（ngram parser 从该版本内置）
--   2. 已在 docker/mysql/conf/ngram.cnf 设置 ngram_token_size=2 并重启 MySQL
--      验证：SHOW VARIABLES LIKE 'ngram_token_size';
--
-- 本脚本幂等：通过 information_schema 判断索引是否存在，可安全重复执行。
-- ---------------------------------------------------------------------------

DROP PROCEDURE IF EXISTS `add_ingredient_fulltext_index`;

DELIMITER $$
CREATE PROCEDURE `add_ingredient_fulltext_index`()
BEGIN
    DECLARE idx_exists INT DEFAULT 0;

    SELECT COUNT(1) INTO idx_exists
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = 'ingredient'
      AND INDEX_NAME   = 'ft_name_desc';

    IF idx_exists = 0 THEN
        -- WITH PARSER ngram 是关键：默认 parser 按空格分词，对中文完全无效
        ALTER TABLE `ingredient`
            ADD FULLTEXT INDEX `ft_name_desc` (`name`, `description`) WITH PARSER ngram;
        SELECT 'FULLTEXT INDEX ft_name_desc 创建成功' AS result;
    ELSE
        SELECT 'FULLTEXT INDEX ft_name_desc 已存在，跳过' AS result;
    END IF;
END$$
DELIMITER ;

CALL `add_ingredient_fulltext_index`();
DROP PROCEDURE IF EXISTS `add_ingredient_fulltext_index`;

-- ---------------------------------------------------------------------------
-- 验证语句（手动执行）：
--   SHOW CREATE TABLE ingredient\G
--   SELECT id, name,
--          MATCH(name, description) AGAINST('+番茄 +炒蛋' IN BOOLEAN MODE) AS score
--   FROM ingredient
--   WHERE MATCH(name, description) AGAINST('+番茄 +炒蛋' IN BOOLEAN MODE)
--   ORDER BY score DESC LIMIT 20;
--
-- 回滚：
--   ALTER TABLE ingredient DROP INDEX ft_name_desc;
-- ---------------------------------------------------------------------------
