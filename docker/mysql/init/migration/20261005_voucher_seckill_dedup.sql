-- ---------------------------------------------------------------------------
-- 迁移：秒杀券一人一单 DB 兜底（生成列 NULL 逃逸）
-- 日期：2026-10-05
--
-- 问题：秒杀券防重只靠 Redis Set，Redis OOM 淘汰/重启丢数据时同一人可刷多张
--       秒杀券（资金相关数据，DB 层零兜底）。普通券支持重复领取，因此
--       不能对全表加 uk(user_id, voucher_id)。
--
-- 方案：voucher_order 冗余 voucher_type 列（下单时写入），
--       生成列 dedup_key 仅对秒杀券生成 `user_id:voucher_id`，普通券恒 NULL
--       （MySQL 唯一索引不约束 NULL —— 已在 mysql:5.7 实测验证），
--       唯一索引建在生成列上。
--
-- 兼容性：VIRTUAL 生成列建二级索引需 MySQL 5.7.8+（本环境 5.7 已验证）。
-- 本脚本幂等：通过 information_schema 判断列/索引是否存在，可安全重复执行。
-- ---------------------------------------------------------------------------

USE intell_recipe;

DROP PROCEDURE IF EXISTS `add_seckill_dedup`;

DELIMITER $$

CREATE PROCEDURE `add_seckill_dedup`()
BEGIN
    DECLARE v_version VARCHAR(32);

    SELECT VERSION() INTO v_version;

    -- ① 冗余券类型列
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'voucher_order'
          AND COLUMN_NAME = 'voucher_type'
    ) THEN
        ALTER TABLE `voucher_order`
            ADD COLUMN `voucher_type` TINYINT NOT NULL DEFAULT 0
            COMMENT '冗余券类型 0:普通券 1:秒杀券（生成列 dedup_key 依赖）'
            AFTER `voucher_id`;
    END IF;

    -- ② 回填存量数据：以 voucher.type 为准
    UPDATE `voucher_order` vo
        JOIN `voucher` v ON vo.`voucher_id` = v.`id`
        SET vo.`voucher_type` = v.`type`
        WHERE vo.`voucher_type` <> v.`type`;

    -- ③ 生成列（仅秒杀券生成去重键，普通券恒 NULL）
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'voucher_order'
          AND COLUMN_NAME = 'dedup_key'
    ) THEN
        IF LEFT(v_version, 3) >= '5.7' THEN
            ALTER TABLE `voucher_order`
                ADD COLUMN `dedup_key` VARCHAR(64) GENERATED ALWAYS AS (
                    IF(`voucher_type` = 1, CONCAT(`user_id`, ':', `voucher_id`), NULL)
                ) VIRTUAL;
        ELSE
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = '生成列需要 MySQL 5.7.8+，请先升级';
        END IF;
    END IF;

    -- ④ 唯一索引
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'voucher_order'
          AND INDEX_NAME = 'uk_seckill_dedup'
    ) THEN
        ALTER TABLE `voucher_order`
            ADD UNIQUE KEY `uk_seckill_dedup` (`dedup_key`);
    END IF;
END$$

DELIMITER ;

CALL `add_seckill_dedup`();
DROP PROCEDURE IF EXISTS `add_seckill_dedup`;

-- ---------------------------------------------------------------------------
-- 验证（预期：seckill_rows 与 dedup_key 非空行数一致；null_rows = 普通券行数）
-- SELECT COUNT(*) AS total,
--        SUM(voucher_type = 1)          AS seckill_rows,
--        SUM(dedup_key IS NOT NULL)     AS dedup_rows,
--        SUM(dedup_key IS NULL)         AS null_rows
-- FROM voucher_order;
-- SHOW INDEX FROM voucher_order WHERE Key_name = 'uk_seckill_dedup';
-- ---------------------------------------------------------------------------
