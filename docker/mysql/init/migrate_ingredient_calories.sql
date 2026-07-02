-- ============================================
-- 重建 ingredient 和 diet_log 表（DROP + CREATE）
-- 数据会重新填入，执行前确认无需保留旧数据
-- ============================================

USE intell_recipe;

-- ============================================
-- 1. 删除旧表
-- ============================================
DROP TABLE IF EXISTS `diet_log`;
DROP TABLE IF EXISTS `ingredient`;

-- ============================================
-- 2. 创建 ingredient 表（含 calories_per_100g 数值型热量字段）
-- ============================================
CREATE TABLE `ingredient` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(64) NOT NULL COMMENT '食材名称',
  `image` varchar(255) DEFAULT NULL COMMENT '食材图片',
  `description` varchar(500) DEFAULT NULL COMMENT '食材描述',
  `nutrition_value` varchar(100) DEFAULT NULL COMMENT '单位热量文案(如:50千卡/100g)',
  `calories_per_100g` decimal(8,1) DEFAULT NULL COMMENT '每100g热量(千卡)，数值型，用于计算',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT 0 COMMENT '逻辑删除 0:未删除 1:已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='食材总表';

-- ============================================
-- 3. 创建 diet_log 表
-- ============================================
CREATE TABLE `diet_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `ingredient_id` bigint(20) DEFAULT NULL COMMENT '食材ID',
  `ingredient_name` varchar(64) NOT NULL COMMENT '食材名称(快照)',
  `calories_per_100g` decimal(8,1) DEFAULT 0 COMMENT '每100g热量(快照，千卡)',
  `grams` decimal(8,1) NOT NULL COMMENT '摄入克数',
  `meal_type` tinyint DEFAULT 0 COMMENT '0:早餐 1:午餐 2:晚餐 3:加餐',
  `log_date` date NOT NULL COMMENT '记录日期',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_date` (`user_id`, `log_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='饮食记录表';

-- ============================================
-- 4. 插入 50 个真实食材（含真实热量）
-- 数据来源：中国食物成分表（第6版）
-- ============================================
INSERT INTO ingredient (id, name, image, description, nutrition_value, calories_per_100g, create_time, update_time) VALUES
(1,  '大米（生）',   NULL, '稻谷去壳后的米粒，主食之一', '346千卡/100g', 346, NOW(), NOW()),
(2,  '小麦粉',       NULL, '小麦磨成的面粉',             '354千卡/100g', 354, NOW(), NOW()),
(3,  '玉米（鲜）',   NULL, '鲜玉米，粗粮主食',           '112千卡/100g', 112, NOW(), NOW()),
(4,  '燕麦（干）',   NULL, '干燕麦片，高纤维谷物',       '389千卡/100g', 389, NOW(), NOW()),
(5,  '红薯',         NULL, '根茎类主食，富含β-胡萝卜素', '99千卡/100g',  99,  NOW(), NOW()),
(6,  '土豆',         NULL, '薯类主食，钾含量丰富',       '81千卡/100g',  81,  NOW(), NOW()),
(7,  '鸡胸肉（生）', NULL, '生鸡胸肉，高蛋白低脂肪',     '133千卡/100g', 133, NOW(), NOW()),
(8,  '猪肉（生）',   NULL, '生瘦猪肉，富含维生素B1',     '143千卡/100g', 143, NOW(), NOW()),
(9,  '牛肉（生）',   NULL, '生瘦牛肉，富含铁和锌',       '106千卡/100g', 106, NOW(), NOW()),
(10, '羊肉（生）',   NULL, '生瘦羊肉，温补肉类',         '118千卡/100g', 118, NOW(), NOW()),
(11, '排骨（生）',   NULL, '猪肋骨，含脂肪较多',         '278千卡/100g', 278, NOW(), NOW()),
(12, '虾仁（生）',   NULL, '生虾仁，高蛋白低脂水产',     '48千卡/100g',  48,  NOW(), NOW()),
(13, '鲤鱼（生）',   NULL, '生鲤鱼，淡水鱼',             '109千卡/100g', 109, NOW(), NOW()),
(14, '带鱼（生）',   NULL, '生带鱼，海产鱼类',           '127千卡/100g', 127, NOW(), NOW()),
(15, '鲫鱼（生）',   NULL, '生鲫鱼，淡水鱼',             '108千卡/100g', 108, NOW(), NOW()),
(16, '海带（鲜）',   NULL, '鲜海带，藻类食材',           '13千卡/100g',  13,  NOW(), NOW()),
(17, '鸡蛋',         NULL, '全营养食品',                 '147千卡/100g', 147, NOW(), NOW()),
(18, '鸭蛋',         NULL, '蛋类食材',                   '180千卡/100g', 180, NOW(), NOW()),
(19, '牛奶',         NULL, '全脂牛奶，优质钙源',         '54千卡/100g',  54,  NOW(), NOW()),
(20, '酸奶',         NULL, '发酵乳制品，含益生菌',       '72千卡/100g',  72,  NOW(), NOW()),
(21, '奶酪',         NULL, '浓缩乳制品，钙含量极高',     '328千卡/100g', 328, NOW(), NOW()),
(22, '黄豆（干）',   NULL, '干黄豆，优质植物蛋白',       '359千卡/100g', 359, NOW(), NOW()),
(23, '绿豆（干）',   NULL, '干绿豆，清热解暑',           '329千卡/100g', 329, NOW(), NOW()),
(24, '红豆（干）',   NULL, '干红豆，补血养颜',           '324千卡/100g', 324, NOW(), NOW()),
(25, '豆腐',         NULL, '北豆腐，大豆制品',           '81千卡/100g',  81,  NOW(), NOW()),
(26, '豆浆',         NULL, '无糖豆浆，植物蛋白',         '31千卡/100g',  31,  NOW(), NOW()),
(27, '白菜',         NULL, '大白菜，冬季常见蔬菜',       '20千卡/100g',  20,  NOW(), NOW()),
(28, '菠菜',         NULL, '铁含量丰富的绿叶菜',         '28千卡/100g',  28,  NOW(), NOW()),
(29, '芹菜',         NULL, '高纤维蔬菜，有助降压',       '20千卡/100g',  20,  NOW(), NOW()),
(30, '西红柿',       NULL, '富含番茄红素，可蔬可果',     '20千卡/100g',  20,  NOW(), NOW()),
(31, '黄瓜',         NULL, '低热量蔬菜，清爽可口',       '16千卡/100g',  16,  NOW(), NOW()),
(32, '胡萝卜',       NULL, '富含β-胡萝卜素，护眼',      '41千卡/100g',  41,  NOW(), NOW()),
(33, '洋葱',         NULL, '调味蔬菜，含硫化物',         '40千卡/100g',  40,  NOW(), NOW()),
(34, '大蒜',         NULL, '调味食材，杀菌消炎',         '149千卡/100g', 149, NOW(), NOW()),
(35, '生姜',         NULL, '调味去腥，温中散寒',         '80千卡/100g',  80,  NOW(), NOW()),
(36, '青椒',         NULL, '富含维生素C的辣椒',         '22千卡/100g',  22,  NOW(), NOW()),
(37, '茄子',         NULL, '紫色蔬菜，含花青素',         '25千卡/100g',  25,  NOW(), NOW()),
(38, '西兰花',       NULL, '营养全面的十字花科蔬菜',     '36千卡/100g',  36,  NOW(), NOW()),
(39, '花菜',         NULL, '十字花科蔬菜，口感清脆',     '24千卡/100g',  24,  NOW(), NOW()),
(40, '蘑菇（鲜）',   NULL, '鲜蘑菇，菌类食材',           '24千卡/100g',  24,  NOW(), NOW()),
(41, '木耳（水发）', NULL, '水发木耳，食用菌',           '27千卡/100g',  27,  NOW(), NOW()),
(42, '苹果',         NULL, '常见水果，富含果胶',         '53千卡/100g',  53,  NOW(), NOW()),
(43, '香蕉',         NULL, '高钾水果，快速补充能量',     '93千卡/100g',  93,  NOW(), NOW()),
(44, '橙子',         NULL, '富含维生素C的柑橘类水果',   '48千卡/100g',  48,  NOW(), NOW()),
(45, '西瓜',         NULL, '夏季消暑水果，水分充足',     '26千卡/100g',  26,  NOW(), NOW()),
(46, '葡萄',         NULL, '含多酚类抗氧化物质',         '44千卡/100g',  44,  NOW(), NOW()),
(47, '草莓',         NULL, '浆果类水果，富含维生素C',   '32千卡/100g',  32,  NOW(), NOW()),
(48, '柠檬',         NULL, '高酸水果，富含维生素C',     '37千卡/100g',  37,  NOW(), NOW()),
(49, '花生（生）',   NULL, '生花生米，富含不饱和脂肪酸', '567千卡/100g', 567, NOW(), NOW()),
(50, '核桃（干）',   NULL, '干核桃仁，补脑坚果',         '654千卡/100g', 654, NOW(), NOW());

-- ============================================
-- 5. 插入饮食记录示例数据
-- ============================================
INSERT INTO diet_log (user_id, ingredient_id, ingredient_name, calories_per_100g, grams, meal_type, log_date, create_time) VALUES
(1, 19, '牛奶',         54,  250, 0, CURDATE(), NOW()),
(1, 17, '鸡蛋',         147, 60,  0, CURDATE(), NOW()),
(1, 4,  '燕麦（干）',   389, 40,  0, CURDATE(), NOW()),
(1, 1,  '大米（生）',   346, 80,  1, CURDATE(), NOW()),
(1, 7,  '鸡胸肉（生）', 133, 150, 1, CURDATE(), NOW()),
(1, 38, '西兰花',       36,  200, 1, CURDATE(), NOW()),
(1, 14, '带鱼（生）',   127, 120, 2, CURDATE(), NOW()),
(1, 25, '豆腐',         81,  150, 2, CURDATE(), NOW()),
(1, 27, '白菜',         20,  200, 2, CURDATE(), NOW()),
(1, 42, '苹果',         53,  200, 3, CURDATE(), NOW());

-- ============================================
-- 6. 验证
-- ============================================
SELECT '=== ingredient 表 ===' AS info;
SELECT id, name, nutrition_value, calories_per_100g FROM ingredient ORDER BY id;

SELECT '=== diet_log 表 ===' AS info;
SELECT id, user_id, ingredient_name, calories_per_100g, grams, meal_type, log_date FROM diet_log ORDER BY id;