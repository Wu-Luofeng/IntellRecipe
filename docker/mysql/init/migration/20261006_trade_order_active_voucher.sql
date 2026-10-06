-- ---------------------------------------------------------------------------
-- 迁移：trade_order 在途券唯一（生成列 NULL 逃逸）
-- 日期：2026-10-06
--
-- 问题：券核销有 useVoucher 的 CAS 兜底（不会重复扣），但跨结算会话并发用
--       同一张券时，第二笔订单会先被受理（status=4）再在异步阶段失败——
--       "优惠券已被使用"，纯体验问题。根因：受理阶段不占用券。
--
-- 方案：生成列 active_voucher_key 仅对"在途/待支付且带券"的订单生成
--       voucher_order_id，其余（终态/无券）恒 NULL 逃逸唯一索引。
--       订单进终态 → 生成列自动变 NULL → 券自动"释放"，无需显式清理。
--
-- 与 OP-1 同一手法（MySQL 无部分唯一索引，用生成列模拟），
-- 本次维度是订单状态，OP-1 维度是券类型。
-- 前置条件：trade_order.voucher_order_id 列已存在（schema 已有）。
-- 本脚本幂等：通过 information_schema 判断列/索引是否存在，可安全重复执行。
-- ---------------------------------------------------------------------------

USE intell_recipe;

DROP PROCEDURE IF EXISTS `add_active_voucher_key`;

DELIMITER $$

CREATE PROCEDURE `add_active_voucher_key`()
BEGIN
    -- ① 生成列（仅 status∈{0,4} 且带券的订单生成去重键）
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'trade_order'
          AND COLUMN_NAME = 'active_voucher_key'
    ) THEN
        ALTER TABLE `trade_order`
            ADD COLUMN `active_voucher_key` BIGINT GENERATED ALWAYS AS (
                IF(`status` IN (0, 4) AND `voucher_order_id` IS NOT NULL,
                   `voucher_order_id`, NULL)
            ) VIRTUAL COMMENT '在途券去重键（终态/无券恒 NULL 逃逸唯一索引）';
    END IF;

    -- ② 唯一索引
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'trade_order'
          AND INDEX_NAME = 'uk_active_voucher'
    ) THEN
        ALTER TABLE `trade_order`
            ADD UNIQUE KEY `uk_active_voucher` (`active_voucher_key`);
    END IF;
END$$

DELIMITER ;

CALL `add_active_voucher_key`();
DROP PROCEDURE IF EXISTS `add_active_voucher_key`;

-- ---------------------------------------------------------------------------
-- 验证（预期：inflight_rows = dedup_rows；total 为订单总数）
-- SELECT COUNT(*) AS total,
--        SUM(status IN (0,4) AND voucher_order_id IS NOT NULL) AS inflight_rows,
--        SUM(active_voucher_key IS NOT NULL)                    AS dedup_rows
-- FROM trade_order;
-- SHOW INDEX FROM trade_order WHERE Key_name = 'uk_active_voucher';
-- ---------------------------------------------------------------------------
