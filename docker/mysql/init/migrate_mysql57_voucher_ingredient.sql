-- MySQL 5.7 compatible. Run once on existing DB:
--   docker exec -i intellrecipe-mysql mysql -uroot -pYOUR_ROOT_PASSWORD intell_recipe < docker/mysql/init/migrate_mysql57_voucher_ingredient.sql
-- Or from host with copied file:
--   docker exec -i intellrecipe-mysql mysql -uroot -proot intell_recipe < migrate_mysql57_voucher_ingredient.sql

USE intell_recipe;

-- ingredient.nutrition_value: add only if missing (MySQL 5.7 has no "ADD COLUMN IF NOT EXISTS")
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'ingredient'
    AND COLUMN_NAME = 'nutrition_value'
);
SET @sql := IF(
  @col_exists = 0,
  'ALTER TABLE `ingredient` ADD COLUMN `nutrition_value` varchar(100) DEFAULT NULL COMMENT ''单位热量值(如:50千卡/100g)'' AFTER `description`',
  'SELECT ''ingredient.nutrition_value already exists'' AS msg'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Optional cleanup: old schema had columns not mapped by Ingredient.java (safe to drop if empty / unused)
-- Uncomment if you want to align DB strictly to entity-only columns:
-- ALTER TABLE `ingredient` DROP COLUMN `category`;
-- ALTER TABLE `ingredient` DROP COLUMN `weight`;
-- ALTER TABLE `ingredient` DROP COLUMN `unit`;
-- ALTER TABLE `ingredient` DROP COLUMN `calories`;

CREATE TABLE IF NOT EXISTS `voucher` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `shop_id` bigint(20) NOT NULL COMMENT '商家ID',
  `title` varchar(255) NOT NULL COMMENT '代金券标题',
  `sub_title` varchar(255) DEFAULT NULL COMMENT '副标题',
  `pay_value` bigint(20) NOT NULL COMMENT '抵扣金额（分）',
  `actual_value` bigint(20) NOT NULL COMMENT '使用门槛（分）',
  `type` tinyint NOT NULL DEFAULT 0 COMMENT '0:普通券 1:秒杀券',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '1:上架 2:下架 3:过期',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_shop_id` (`shop_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='优惠券表';

CREATE TABLE IF NOT EXISTS `seckill_voucher` (
  `voucher_id` bigint(20) NOT NULL COMMENT '关联优惠券ID',
  `stock` int(11) NOT NULL COMMENT '库存',
  `begin_time` datetime NOT NULL COMMENT '秒杀开始时间',
  `end_time` datetime NOT NULL COMMENT '秒杀结束时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`voucher_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀优惠券扩展表';

CREATE TABLE IF NOT EXISTS `voucher_order` (
  `id` bigint(20) NOT NULL COMMENT '订单ID（雪花算法）',
  `user_id` bigint(20) NOT NULL COMMENT '下单用户ID',
  `voucher_id` bigint(20) NOT NULL COMMENT '购买的优惠券ID',
  `pay_type` tinyint DEFAULT 1 COMMENT '支付方式',
  `status` tinyint DEFAULT 1 COMMENT '订单状态',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
  `pay_time` datetime DEFAULT NULL COMMENT '支付时间',
  `use_time` datetime DEFAULT NULL COMMENT '核销时间',
  `refund_time` datetime DEFAULT NULL COMMENT '退款时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_voucher_id` (`voucher_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='优惠券订单表';

SHOW TABLES;
