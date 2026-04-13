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
  `name` varchar(50) NOT NULL COMMENT '商家名称',
  `address` varchar(255) DEFAULT NULL COMMENT '商家地址',
  `phone` varchar(20) DEFAULT NULL COMMENT '商家电话',
  `image` varchar(255) DEFAULT NULL COMMENT '商家图片',
  `score` decimal(2,1) DEFAULT 5.0 COMMENT '商家评分',
  `description` varchar(500) DEFAULT NULL COMMENT '商家描述',
  `open_time` varchar(50) DEFAULT NULL COMMENT '营业时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT 0 COMMENT '逻辑删除 0:未删除 1:已删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商家表';

DROP TABLE IF EXISTS `ingredient`;
CREATE TABLE `ingredient` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(64) NOT NULL COMMENT '食材名称',
  `category` varchar(50) DEFAULT NULL COMMENT '食材分类',
  `description` varchar(500) DEFAULT NULL COMMENT '食材描述',
  `image` varchar(255) DEFAULT NULL COMMENT '食材图片',
  `weight` decimal(10,2) DEFAULT NULL COMMENT '标准重量',
  `unit` varchar(20) DEFAULT NULL COMMENT '计量单位',
  `calories` decimal(10,2) DEFAULT NULL COMMENT '热量(千卡)',
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
  `name` varchar(64) NOT NULL COMMENT '商品名称',
  `price` decimal(10,2) NOT NULL COMMENT '商品价格',
  `image` varchar(255) DEFAULT NULL COMMENT '商品图片',
  `description` varchar(500) DEFAULT NULL COMMENT '商品描述',
  `weight` decimal(10,2) DEFAULT NULL COMMENT '商品重量',
  `unit` varchar(20) DEFAULT NULL COMMENT '计量单位',
  `status` tinyint DEFAULT 1 COMMENT '状态 1:上架 0:下架',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT 0 COMMENT '逻辑删除 0:未删除 1:已删除',
  PRIMARY KEY (`id`),
  KEY `idx_merchant_id` (`merchant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表';

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
