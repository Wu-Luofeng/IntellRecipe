-- =============================================================================
-- Migration v5：下单幂等键（D1/D4 修复）
-- 幂等：trade_order 增加 client_token，并对 (user_id, client_token) 建唯一索引。
-- 前端一次结算生成一个 token；重复提交命中即返回原订单，数据库层兜底防重。
-- =============================================================================
USE intell_recipe;

SET @ct := (SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trade_order' AND COLUMN_NAME = 'client_token');
SET @sqlct := IF(@ct = 0,
    'ALTER TABLE `trade_order` ADD COLUMN `client_token` varchar(40) DEFAULT NULL COMMENT ''结算幂等键（同一结算会话唯一）''',
    'SELECT 1');
PREPARE stmtct FROM @sqlct; EXECUTE stmtct; DEALLOCATE PREPARE stmtct;

SET @ui := (SELECT COUNT(*) FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trade_order' AND INDEX_NAME = 'uk_user_client_token');
SET @sqlui := IF(@ui = 0,
    'ALTER TABLE `trade_order` ADD UNIQUE KEY `uk_user_client_token` (`user_id`, `client_token`)',
    'SELECT 1');
PREPARE stmtui FROM @sqlui; EXECUTE stmtui; DEALLOCATE PREPARE stmtui;
