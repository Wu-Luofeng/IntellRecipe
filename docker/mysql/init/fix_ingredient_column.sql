-- 修复 ingredient 表列名不一致问题
-- 问题：schema.sql 中列名为 calories_per_100g，但 MyBatis-Plus 驼峰映射 caloriesPer100g -> calories_per100g
-- 方案：将数据库列名改为 calories_per100g 以匹配 MyBatis-Plus 默认映射

USE intell_recipe;

-- 检查并重命名列（兼容已有 calories_per_100g 和缺失两种情况）
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'intell_recipe' AND TABLE_NAME = 'ingredient' AND COLUMN_NAME = 'calories_per_100g');

SET @col_target_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'intell_recipe' AND TABLE_NAME = 'ingredient' AND COLUMN_NAME = 'calories_per100g');

-- 源列存在且目标列不存在 -> 重命名
SET @sql = IF(@col_exists = 1 AND @col_target_exists = 0,
    'ALTER TABLE `ingredient` CHANGE COLUMN `calories_per_100g` `calories_per100g` decimal(8,1) DEFAULT NULL COMMENT ''每100g热量(千卡)，数值型，用于计算''',
    'SELECT ''no rename needed'' AS msg');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 同步修复 diet_log 表（保持一致，虽然 diet-service 用的是快照字段，此处统一命名）
SET @dl_col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'intell_recipe' AND TABLE_NAME = 'diet_log' AND COLUMN_NAME = 'calories_per_100g');

SET @dl_target_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'intell_recipe' AND TABLE_NAME = 'diet_log' AND COLUMN_NAME = 'calories_per100g');

SET @dl_sql = IF(@dl_col_exists = 1 AND @dl_target_exists = 0,
    'ALTER TABLE `diet_log` CHANGE COLUMN `calories_per_100g` `calories_per100g` decimal(8,1) DEFAULT 0 COMMENT ''每100g热量(快照，千卡)''',
    'SELECT ''diet_log no rename needed'' AS msg');

PREPARE dl_stmt FROM @dl_sql;
EXECUTE dl_stmt;
DEALLOCATE PREPARE dl_stmt;

-- 验证结果
SELECT 'ingredient columns:' AS info;
SHOW COLUMNS FROM `ingredient`;
SELECT 'diet_log columns:' AS info;
SHOW COLUMNS FROM `diet_log`;