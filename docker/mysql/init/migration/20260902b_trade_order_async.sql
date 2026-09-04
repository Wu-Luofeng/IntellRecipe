-- =============================================================================
-- Migration v2（单表模型）：异步下单不再用独立受理表，订单表自留痕
-- 在已执行 20260902_trade_order.sql 的库上执行一次（幂等）
--   1) trade_order 增加 fail_reason（下单失败原因）与组合索引 (status, create_time)
--   2) 状态扩展：4=处理中(已受理，待异步处理/补偿)  5=下单失败
--      原：0=待支付 1=已支付 2=已完成 3=已取消
-- =============================================================================
USE intell_recipe;

SET @col3 := (SELECT COUNT(*) FROM information_schema.COLUMNS
              WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trade_order' AND COLUMN_NAME = 'fail_reason');
SET @sql3 := IF(@col3 = 0,
    'ALTER TABLE `trade_order` ADD COLUMN `fail_reason` varchar(500) DEFAULT NULL COMMENT ''下单失败原因（status=5）''',
    'SELECT 1');
PREPARE stmt3 FROM @sql3; EXECUTE stmt3; DEALLOCATE PREPARE stmt3;

-- 供补偿定时任务扫描：WHERE status=4 AND create_time < ? ORDER BY create_time
SET @idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trade_order' AND INDEX_NAME = 'idx_status_time');
SET @sql4 := IF(@idx = 0,
    'ALTER TABLE `trade_order` ADD INDEX `idx_status_time` (`status`, `create_time`)',
    'SELECT 1');
PREPARE stmt4 FROM @sql4; EXECUTE stmt4; DEALLOCATE PREPARE stmt4;

-- 更新状态注释（仅注释，不影响数据）
ALTER TABLE `trade_order`
  MODIFY COLUMN `status` tinyint NOT NULL DEFAULT 4
    COMMENT '4:处理中 0:待支付 1:已支付 2:已完成 3:已取消 5:下单失败';