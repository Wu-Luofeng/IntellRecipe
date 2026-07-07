-- 购物车表迁移脚本（用于已有数据库增量执行）
USE intell_recipe;

DROP TABLE IF EXISTS `cart`;
CREATE TABLE `cart` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `product_id` bigint(20) NOT NULL COMMENT '商品ID',
  `merchant_id` bigint(20) NOT NULL COMMENT '商家ID',
  `product_name` varchar(64) NOT NULL COMMENT '商品名称(快照)',
  `product_image` varchar(255) DEFAULT NULL COMMENT '商品图片(快照)',
  `product_price` decimal(10,2) NOT NULL COMMENT '商品单价(快照)',
  `quantity` int NOT NULL DEFAULT 1 COMMENT '数量',
  `selected` tinyint DEFAULT 1 COMMENT '是否选中 0:未选中 1:选中',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_product` (`user_id`, `product_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购物车表';