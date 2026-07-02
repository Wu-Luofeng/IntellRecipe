-- Migration: 为优惠券增加有效期功能
-- voucher 表增加 validity_days 字段
ALTER TABLE `voucher` ADD COLUMN `validity_days` int DEFAULT NULL COMMENT '有效期（天），购买后在此天数后过期' AFTER `status`;

-- voucher_order 表增加 expire_time 字段
ALTER TABLE `voucher_order` ADD COLUMN `expire_time` datetime DEFAULT NULL COMMENT '过期时间（创建时间+有效期）' AFTER `create_time`;

-- 为过期时间添加索引，便于惰性过期批量更新
ALTER TABLE `voucher_order` ADD INDEX `idx_expire_time` (`expire_time`);