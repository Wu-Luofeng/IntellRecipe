-- recipe-service 建表

-- 1. 食谱表
CREATE TABLE IF NOT EXISTS `recipe` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(100) NOT NULL COMMENT '食谱名称',
  `description` text COMMENT '食谱描述',
  `cuisine_type` varchar(50) DEFAULT NULL COMMENT '菜系类型：川菜/粤菜/西餐/日料/家常菜',
  `taste_profile` varchar(100) DEFAULT NULL COMMENT '口味标签：辣/甜/清淡/鲜/香（逗号分隔）',
  `cooking_time` int(11) DEFAULT NULL COMMENT '烹饪时间（分钟）',
  `difficulty` tinyint(4) DEFAULT 1 COMMENT '难度：1-简单 2-中等 3-困难',
  `calories` int(11) DEFAULT NULL COMMENT '总热量（千卡）',
  `protein` decimal(10,2) DEFAULT NULL COMMENT '蛋白质（克）',
  `fat` decimal(10,2) DEFAULT NULL COMMENT '脂肪（克）',
  `carbs` decimal(10,2) DEFAULT NULL COMMENT '碳水化合物（克）',
  `image` varchar(255) DEFAULT NULL COMMENT '图片URL',
  `steps` text COMMENT '烹饪步骤（JSON数组）',
  `status` tinyint(4) DEFAULT 1 COMMENT '状态：0-下架 1-上架',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(4) DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_cuisine` (`cuisine_type`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='食谱表';

-- 2. 食谱-食材关联表
CREATE TABLE IF NOT EXISTS `recipe_ingredient` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `recipe_id` bigint(20) NOT NULL COMMENT '食谱ID',
  `ingredient_id` bigint(20) NOT NULL COMMENT '食材ID',
  `amount` decimal(10,2) DEFAULT NULL COMMENT '用量',
  `unit` varchar(20) DEFAULT '克' COMMENT '单位：克/毫升/个',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_recipe` (`recipe_id`),
  KEY `idx_ingredient` (`ingredient_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='食谱食材关联表';

-- 3. 用户显式偏好表
CREATE TABLE IF NOT EXISTS `user_preference` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `cuisine_preference` varchar(255) DEFAULT NULL COMMENT '偏好菜系（JSON数组）',
  `taste_preference` varchar(255) DEFAULT NULL COMMENT '偏好口味（JSON数组）',
  `avoid_ingredients` varchar(255) DEFAULT NULL COMMENT '忌口食材（JSON数组）',
  `health_goal` varchar(50) DEFAULT NULL COMMENT '健康目标：减脂/增肌/维持',
  `daily_calorie_target` int(11) DEFAULT NULL COMMENT '每日目标热量',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户显式偏好表';

-- 4. 推荐记录表
CREATE TABLE IF NOT EXISTS `recipe_recommendation_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `recipe_id` bigint(20) NOT NULL COMMENT '推荐的食谱ID',
  `recommendation_type` varchar(50) DEFAULT 'personalized' COMMENT '推荐类型：daily/weekly/personalized',
  `accepted` tinyint(4) DEFAULT 0 COMMENT '是否接受：0-未操作 1-已接受 2-已拒绝',
  `feedback_score` tinyint(4) DEFAULT NULL COMMENT '用户评分：1-5星',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_recipe` (`recipe_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='推荐记录表';
