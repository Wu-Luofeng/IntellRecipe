-- =============================================================================
-- Migration v4：整单模型（一次结算=一个订单，不再按商家拆单）
-- 幂等；将 trade_order 的商家列放开为可空，并新增“券所属商家”列用于按商家小计校验门槛。
-- =============================================================================
USE intell_recipe;

ALTER TABLE `trade_order`
  MODIFY COLUMN `merchant_id` bigint(20) DEFAULT NULL COMMENT '商家ID（多商家整单为空，以明细为准）',
  MODIFY COLUMN `merchant_name` varchar(64) DEFAULT NULL COMMENT '商家名称快照（多商家订单可空）';

SET @vc := (SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trade_order' AND COLUMN_NAME = 'voucher_merchant_id');
SET @sqlv := IF(@vc = 0,
    'ALTER TABLE `trade_order` ADD COLUMN `voucher_merchant_id` bigint(20) DEFAULT NULL COMMENT ''所用优惠券所属商家ID（按该商家小计校验门槛）''',
    'SELECT 1');
PREPARE stmtv FROM @sqlv; EXECUTE stmtv; DEALLOCATE PREPARE stmtv;
