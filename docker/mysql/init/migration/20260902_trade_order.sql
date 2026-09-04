-- =============================================================================
-- Migration: 商城订单体系（购物车结算 → 订单闭环）
-- 在既有云数据库执行一次（DataGrip 中运行本文件即可；可重复执行，全部幂等）
--   1) voucher_order 增加“关联商城订单”列（结算抵现核销时回填，供取消订单退券）
--   2) 新增 trade_order / trade_order_item / trade_order_status_log
-- 状态约定：
--   trade_order.status     0=待支付 1=已支付 2=已完成 3=已取消
--   voucher_order.status   1=待支付 2=已支付(可用) 3=已核销(结算抵现/到店核销)
-- =============================================================================
USE intell_recipe;

-- ---------------------------------------------------------------------------
-- 1. voucher_order 增加关联商城订单列（MySQL 5.7 无 ADD COLUMN IF NOT EXISTS，用存储过程判断幂等）
-- ---------------------------------------------------------------------------
SET @col1 := (SELECT COUNT(*) FROM information_schema.COLUMNS
              WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'voucher_order' AND COLUMN_NAME = 'used_order_no');
SET @sql1 := IF(@col1 = 0,
    'ALTER TABLE voucher_order ADD COLUMN `used_order_no` varchar(32) DEFAULT NULL COMMENT ''关联商城订单号（结算抵现核销）''',
    'SELECT 1');
PREPARE stmt1 FROM @sql1; EXECUTE stmt1; DEALLOCATE PREPARE stmt1;

SET @col2 := (SELECT COUNT(*) FROM information_schema.COLUMNS
              WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'voucher_order' AND COLUMN_NAME = 'used_order_id');
SET @sql2 := IF(@col2 = 0,
    'ALTER TABLE voucher_order ADD COLUMN `used_order_id` bigint(20) DEFAULT NULL COMMENT ''关联商城订单主键ID''',
    'SELECT 1');
PREPARE stmt2 FROM @sql2; EXECUTE stmt2; DEALLOCATE PREPARE stmt2;

-- ---------------------------------------------------------------------------
-- 2. 商城订单主表（一个订单 = 一个商家一次结算）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `trade_order` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_no` varchar(32) NOT NULL COMMENT '业务订单号（全局唯一，对外展示/追溯）',
  `user_id` bigint(20) NOT NULL COMMENT '下单用户ID',
  `merchant_id` bigint(20) NOT NULL COMMENT '商家ID',
  `merchant_name` varchar(64) NOT NULL COMMENT '商家名称（快照）',
  `total_amount` decimal(10,2) NOT NULL COMMENT '商品总额',
  `discount_amount` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '优惠券抵扣金额',
  `pay_amount` decimal(10,2) NOT NULL COMMENT '应付金额 = total - discount',
  `voucher_order_id` bigint(20) DEFAULT NULL COMMENT '使用的券实例ID（voucher_order.id）',
  `voucher_title` varchar(255) DEFAULT NULL COMMENT '使用的券标题（快照）',
  `receiver_name` varchar(50) DEFAULT NULL COMMENT '收货人',
  `receiver_phone` varchar(20) DEFAULT NULL COMMENT '收货电话',
  `receiver_address` varchar(255) DEFAULT NULL COMMENT '收货地址',
  `remark` varchar(255) DEFAULT NULL COMMENT '买家备注',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0:待支付 1:已支付 2:已完成 3:已取消',
  `pay_time` datetime DEFAULT NULL COMMENT '支付时间',
  `finish_time` datetime DEFAULT NULL COMMENT '完成时间',
  `cancel_time` datetime DEFAULT NULL COMMENT '取消时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_merchant_id` (`merchant_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商城订单表';

-- ---------------------------------------------------------------------------
-- 3. 商城订单明细（商品快照，保障可追溯）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `trade_order_item` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_no` varchar(32) NOT NULL COMMENT '订单号',
  `merchant_id` bigint(20) NOT NULL COMMENT '商家ID',
  `product_id` bigint(20) NOT NULL COMMENT '商品ID',
  `product_name` varchar(64) NOT NULL COMMENT '商品名称（快照）',
  `product_image` varchar(255) DEFAULT NULL COMMENT '商品图片（快照）',
  `price` decimal(10,2) NOT NULL COMMENT '成交单价（快照）',
  `quantity` int NOT NULL COMMENT '购买数量',
  `subtotal` decimal(10,2) NOT NULL COMMENT '小计 = price * quantity',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_order_no` (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商城订单明细表';

-- ---------------------------------------------------------------------------
-- 4. 订单状态流转日志（可追溯：谁在什么时间把订单从什么状态变到什么状态）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `trade_order_status_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_no` varchar(32) NOT NULL COMMENT '订单号',
  `from_status` tinyint DEFAULT NULL COMMENT '原状态',
  `order_status` tinyint NOT NULL COMMENT '变更后状态',
  `operator` varchar(32) DEFAULT NULL COMMENT '操作人/系统',
  `remark` varchar(255) DEFAULT NULL COMMENT '说明',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
  PRIMARY KEY (`id`),
  KEY `idx_order_no` (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单状态流转日志表';
