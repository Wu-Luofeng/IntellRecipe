-- ---------------------------------------------------------------------------
-- ngram 全文索引验证脚本（只读，可反复执行）
--
-- 用途：确认 ES 降级到 MySQL 的这条链路是否真实可用。
-- 执行：mysql -h127.0.0.1 -P3307 -uroot -p intell_recipe < verify-ngram-fulltext.sql
--       或在 Kibana/Navicat 里逐段执行。
--
-- 每项都标注了【期望结果】，不符合就说明对应环节没配好。
-- ---------------------------------------------------------------------------

USE `intell_recipe`;

-- ① ngram_token_size 必须是 2（与 MysqlFullTextSearchStrategy 的配置一致）
--    期望：Value = 2
--    若为 1 或其它值：说明 ngram.cnf 没挂载 / MySQL 没重启，见 docker/mysql/conf/ngram.cnf
SHOW VARIABLES LIKE 'ngram_token_size';

-- ② FULLTEXT 索引是否存在，且分词器是 ngram
--    期望：结果里有 Key_name = ft_name_desc，且建表语句带 WITH PARSER ngram
--    若为空：执行 docker/mysql/init/migration/20260917_ingredient_fulltext.sql
SHOW INDEX FROM ingredient WHERE Key_name = 'ft_name_desc';
SHOW CREATE TABLE ingredient\G

-- ③ 基础数据检查
--    期望：deleted=0 的食材行数 > 0
SELECT COUNT(*) AS total_ingredient FROM ingredient WHERE deleted = 0;

-- ---------------------------------------------------------------------------
-- ④ 多字检索（≥2 字）—— 走 ngram 全文索引
--    对应 MysqlFullTextSearchStrategy，查询串形如 '+番茄 +茄炒 +炒蛋'
--    期望：返回匹配行，score > 0，且按 score 降序
-- ---------------------------------------------------------------------------
SELECT id, name,
       MATCH(name, description) AGAINST('+番茄 +茄炒 +炒蛋' IN BOOLEAN MODE) AS score
FROM ingredient
WHERE deleted = 0
  AND MATCH(name, description) AGAINST('+番茄 +茄炒 +炒蛋' IN BOOLEAN MODE)
ORDER BY score DESC
LIMIT 20;

-- ④-b 执行计划确认：key 必须是 ft_name_desc（走索引，不是全表扫描）
--     期望：key = ft_name_desc，type = fulltext
--     若 key = NULL 且 type = ALL：说明索引没生效，回退成全表扫描
EXPLAIN
SELECT id, name
FROM ingredient
WHERE deleted = 0
  AND MATCH(name, description) AGAINST('+番茄 +茄炒 +炒蛋' IN BOOLEAN MODE)\G

-- ---------------------------------------------------------------------------
-- ⑤ 单字检索—— 应用层会跳过全文索引，走 LIKE（IngredientMapper.searchByLike）
--    期望：能查到包含该字的食材
--    注意：这一条本来就是全表扫描（EXPLAIN 里 type=ALL 是正常的，不是问题）
-- ---------------------------------------------------------------------------
SELECT id, name
FROM ingredient
WHERE deleted = 0
  AND name LIKE '%姜%'
LIMIT 20;

-- ---------------------------------------------------------------------------
-- ⑥ 分词验证（可选，需 SUPER 权限）：看 MySQL 实际把文档切成了哪些 token
--    期望：word 列出现 "番茄" "茄炒" "炒蛋" 这类二字组合
--    说明：需要先把库名填进 innodb_ft_aux_table，格式 '库名/表名'
-- ---------------------------------------------------------------------------
-- SET GLOBAL innodb_ft_aux_table = 'intell_recipe/ingredient';
-- SELECT DISTINCT word FROM information_schema.INNODB_FT_INDEX_TABLE LIMIT 50;
-- SELECT DISTINCT word FROM information_schema.INNODB_FT_INDEX_CACHE LIMIT 50;

-- ---------------------------------------------------------------------------
-- ⑦ 负向用例：单字直接走 MATCH 会失败（这正是应用层要回退 LIKE 的原因）
--    期望：以下语句报错 或 返回空 —— 因为 ngram_token_size=2 时单字切不出 token
-- ---------------------------------------------------------------------------
-- SELECT id FROM ingredient WHERE MATCH(name, description) AGAINST('+姜' IN BOOLEAN MODE);

-- ---------------------------------------------------------------------------
-- 回滚（如需）：
--   ALTER TABLE ingredient DROP INDEX ft_name_desc;
-- ---------------------------------------------------------------------------
