CREATE TABLE IF NOT EXISTS `voucher` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `shop_id` bigint(20) DEFAULT NULL COMMENT '商户id',
  `title` varchar(255) NOT NULL COMMENT '代金券标题',
  `sub_title` varchar(255) DEFAULT NULL COMMENT '副标题',
  `actual_value` bigint(10) NOT NULL COMMENT '抵扣金额',
  `pay_value` bigint(10) NOT NULL COMMENT '支付金额',
  `status` tinyint(1) NOT NULL DEFAULT '1' COMMENT '状态 1上架 2下架 3过期',
  `type` tinyint(1) NOT NULL DEFAULT '0' COMMENT '0普通券 1秒杀券',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `seckill_voucher` (
  `voucher_id` bigint(20) NOT NULL COMMENT '关联的优惠券id',
  `stock` int(11) NOT NULL COMMENT '库存',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `begin_time` datetime NOT NULL COMMENT '生效时间',
  `end_time` datetime NOT NULL COMMENT '失效时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`voucher_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `voucher_order` (
  `id` bigint(20) NOT NULL COMMENT '主键',
  `user_id` bigint(20) NOT NULL COMMENT '下单的用户id',
  `voucher_id` bigint(20) NOT NULL COMMENT '购买的代金券id',
  `pay_type` tinyint(1) DEFAULT '1' COMMENT '支付方式 1：余额支付；2：支付宝；3：微信',
  `status` tinyint(1) DEFAULT '1' COMMENT '订单状态，1：未支付；2：已支付；3：已核销；4：已取消；5：退款中；6：已退款',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
  `pay_time` datetime DEFAULT NULL COMMENT '支付时间',
  `use_time` datetime DEFAULT NULL COMMENT '核销时间',
  `refund_time` datetime DEFAULT NULL COMMENT '退款时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE DATABASE IF NOT EXISTS intell_recipe DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE intell_recipe;

DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `phone` varchar(20) NOT NULL COMMENT '手机号',
  `nickname` varchar(50) DEFAULT NULL COMMENT '昵称',
  `password` varchar(100) DEFAULT NULL COMMENT '密码',
  `avatar` varchar(255) DEFAULT NULL COMMENT '头像',
  `status` tinyint DEFAULT 0 COMMENT '状态 0:正常 1:禁用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT 0 COMMENT '逻辑删除 0:未删除 1:已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

DROP TABLE IF EXISTS `merchant`;
CREATE TABLE `merchant` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(50) NOT NULL COMMENT '商超名称',
  `address` varchar(255) DEFAULT NULL COMMENT '商超地址',
  `phone` varchar(20) DEFAULT NULL COMMENT '联系电话',
  `image` varchar(255) DEFAULT NULL COMMENT '商超图片',
  `score` decimal(2,1) DEFAULT 5.0 COMMENT '商超评分',
  `description` varchar(500) DEFAULT NULL COMMENT '商超描述',
  `open_time` varchar(50) DEFAULT NULL COMMENT '营业时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT 0 COMMENT '逻辑删除 0:未删除 1:已删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商超表';

DROP TABLE IF EXISTS `ingredient`;
CREATE TABLE `ingredient` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(64) NOT NULL COMMENT '食材名称',
  `description` varchar(500) DEFAULT NULL COMMENT '食材描述',
  `image` varchar(255) DEFAULT NULL COMMENT '食材图片',
  `nutrition_value` varchar(50) DEFAULT NULL COMMENT '单位热量值 (如: 50千卡/100g)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT 0 COMMENT '逻辑删除 0:未删除 1:已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='食材总表';

DROP TABLE IF EXISTS `product`;
CREATE TABLE `product` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_id` bigint(20) NOT NULL COMMENT '商家ID',
  `name` varchar(100) NOT NULL COMMENT '商品名称',
  `price` decimal(10,2) NOT NULL COMMENT '价格(元)',
  `image` varchar(255) DEFAULT NULL COMMENT '商品图片',
  `description` varchar(500) DEFAULT NULL COMMENT '商品描述',
  `weight` decimal(10,2) DEFAULT NULL COMMENT '商品重量',
  `unit` varchar(20) DEFAULT NULL COMMENT '计量单位 (如: g, kg, 个)',
  `status` tinyint DEFAULT 1 COMMENT '状态 1:上架 0:下架',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT 0 COMMENT '逻辑删除 0:未删除 1:已删除',
  PRIMARY KEY (`id`),
  KEY `idx_merchant_id` (`merchant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商家商品表';

DROP TABLE IF EXISTS `product_ingredient`;
CREATE TABLE `product_ingredient` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `product_id` bigint(20) NOT NULL COMMENT '商品ID',
  `ingredient_id` bigint(20) NOT NULL COMMENT '食材ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT 0 COMMENT '逻辑删除 0:未删除 1:已删除',
  PRIMARY KEY (`id`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_ingredient_id` (`ingredient_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品食材关联表';
