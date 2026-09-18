-- =============================================================================
-- Migration v6：异步下单补偿工程化（对标 mcdmc：重试计数 + 退避 + 上限收敛）
-- 幂等；在已执行 v1~v5 的库上执行一次
--   1) trade_order 增加 retry_count（MQ 消费/补偿失败时递增）
--   2) 补偿任务按 retry_count 线性退避扫描，达上限置 status=5 并释放 client_token
-- 配套代码：OrderCompensateTask / OrderServiceImpl.incrRetryCount
-- =============================================================================
USE intell_recipe;

SET @rc := (SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trade_order' AND COLUMN_NAME = 'retry_count');
SET @sqlrc := IF(@rc = 0,
    'ALTER TABLE `trade_order` ADD COLUMN `retry_count` int NOT NULL DEFAULT 0 COMMENT ''异步处理重试次数（MQ 消费/补偿失败递增，达上限置失败）''',
    'SELECT 1');
PREPARE stmtrc FROM @sqlrc; EXECUTE stmtrc; DEALLOCATE PREPARE stmtrc;
