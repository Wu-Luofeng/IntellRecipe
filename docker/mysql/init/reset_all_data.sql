-- 设置字符集
SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;
SET character_set_connection = utf8mb4;
SET character_set_database = utf8mb4;
SET character_set_results = utf8mb4;

USE intell_recipe;

-- ============================================
-- 第一部分：清空所有旧数据
-- ============================================
DELETE FROM diet_log;
DELETE FROM voucher_order;
DELETE FROM seckill_voucher;
DELETE FROM voucher;
DELETE FROM product_ingredient;
DELETE FROM product;
DELETE FROM ingredient;
DELETE FROM merchant;

-- 重置自增ID
ALTER TABLE merchant AUTO_INCREMENT = 1;
ALTER TABLE ingredient AUTO_INCREMENT = 1;
ALTER TABLE product AUTO_INCREMENT = 1;
ALTER TABLE voucher AUTO_INCREMENT = 80010;

-- ============================================
-- 第二部分：插入 6 个简化商家
-- ============================================
INSERT INTO merchant (id, name, address, phone, image, score, description, open_time, create_time, update_time) VALUES
(1, '水果店', '幸福路88号', '13800000001', NULL, 4.8, '新鲜水果，产地直供', '08:00-21:00', NOW(), NOW()),
(2, '杂货超市', '人民路66号', '13800000002', NULL, 4.5, '日常杂货，一站购齐', '07:00-22:00', NOW(), NOW()),
(3, '蔬菜摊', '农贸市场A区12号', '13800000003', NULL, 4.6, '当季蔬菜，绿色健康', '06:00-19:00', NOW(), NOW()),
(4, '肉铺', '农贸市场B区5号', '13800000004', NULL, 4.7, '新鲜肉类，品质保证', '06:00-20:00', NOW(), NOW()),
(5, '海鲜店', '海滨路23号', '13800000005', NULL, 4.9, '活鲜海鲜，每日到货', '08:00-20:00', NOW(), NOW()),
(6, '烘焙坊', '步行街18号', '13800000006', NULL, 4.8, '手工烘焙，新鲜出炉', '09:00-21:00', NOW(), NOW());

-- ============================================
-- 第三部分：插入 50 个真实食材（含真实热量）
-- 数据来源：中国食物成分表（第6版）
-- nutrition_value: 文案；calories_per_100g: 数值型，用于计算
-- ============================================
INSERT INTO ingredient (id, name, image, description, nutrition_value, calories_per_100g, create_time, update_time) VALUES
-- 主食类
(1,  '大米（生）',   NULL, '稻谷去壳后的米粒，主食之一，煮熟后约116千卡/100g',     '346千卡/100g', 346, NOW(), NOW()),
(2,  '小麦粉',       NULL, '小麦磨成的面粉，制作面食的基础原料',                 '354千卡/100g', 354, NOW(), NOW()),
(3,  '玉米（鲜）',   NULL, '鲜玉米，粗粮主食，富含膳食纤维',                     '112千卡/100g', 112, NOW(), NOW()),
(4,  '燕麦（干）',   NULL, '干燕麦片，高纤维谷物，适合早餐',                     '389千卡/100g', 389, NOW(), NOW()),
(5,  '红薯',         NULL, '根茎类主食，富含β-胡萝卜素',                       '99千卡/100g',  99,  NOW(), NOW()),
(6,  '土豆',         NULL, '薯类主食，钾含量丰富',                             '81千卡/100g',  81,  NOW(), NOW()),
-- 肉类
(7,  '鸡胸肉（生）', NULL, '生鸡胸肉，高蛋白低脂肪，健身首选',                   '133千卡/100g', 133, NOW(), NOW()),
(8,  '猪肉（生）',   NULL, '生瘦猪肉，富含维生素B1',                           '143千卡/100g', 143, NOW(), NOW()),
(9,  '牛肉（生）',   NULL, '生瘦牛肉，富含铁和锌',                             '106千卡/100g', 106, NOW(), NOW()),
(10, '羊肉（生）',   NULL, '生瘦羊肉，温补肉类，冬季进补佳品',                   '118千卡/100g', 118, NOW(), NOW()),
(11, '排骨（生）',   NULL, '猪肋骨，含脂肪较多，适合炖汤红烧',                   '278千卡/100g', 278, NOW(), NOW()),
-- 水产类
(12, '虾仁（生）',   NULL, '生虾仁，高蛋白低脂水产，肉质鲜嫩',                   '48千卡/100g',  48,  NOW(), NOW()),
(13, '鲤鱼（生）',   NULL, '生鲤鱼，淡水鱼，肉质细嫩',                         '109千卡/100g', 109, NOW(), NOW()),
(14, '带鱼（生）',   NULL, '生带鱼，海产鱼类，富含不饱和脂肪酸',                 '127千卡/100g', 127, NOW(), NOW()),
(15, '鲫鱼（生）',   NULL, '生鲫鱼，淡水鱼，适合炖汤',                         '108千卡/100g', 108, NOW(), NOW()),
(16, '海带（鲜）',   NULL, '鲜海带，藻类食材，富含碘元素',                       '13千卡/100g',  13,  NOW(), NOW()),
-- 蛋奶类
(17, '鸡蛋',         NULL, '全营养食品，蛋白质吸收率高',                       '147千卡/100g', 147, NOW(), NOW()),
(18, '鸭蛋',         NULL, '蛋类食材，适合腌制',                               '180千卡/100g', 180, NOW(), NOW()),
(19, '牛奶',         NULL, '全脂牛奶，优质钙源，营养全面',                       '54千卡/100g',  54,  NOW(), NOW()),
(20, '酸奶',         NULL, '发酵乳制品，含益生菌',                             '72千卡/100g',  72,  NOW(), NOW()),
(21, '奶酪',         NULL, '浓缩乳制品，钙含量极高',                           '328千卡/100g', 328, NOW(), NOW()),
-- 豆类及豆制品
(22, '黄豆（干）',   NULL, '干黄豆，优质植物蛋白来源',                         '359千卡/100g', 359, NOW(), NOW()),
(23, '绿豆（干）',   NULL, '干绿豆，清热解暑豆类',                             '329千卡/100g', 329, NOW(), NOW()),
(24, '红豆（干）',   NULL, '干红豆，补血养颜豆类',                             '324千卡/100g', 324, NOW(), NOW()),
(25, '豆腐',         NULL, '北豆腐，大豆制品，口感嫩滑',                       '81千卡/100g',  81,  NOW(), NOW()),
(26, '豆浆',         NULL, '无糖豆浆，大豆研磨饮品，植物蛋白',                   '31千卡/100g',  31,  NOW(), NOW()),
-- 蔬菜类
(27, '白菜',         NULL, '大白菜，冬季常见蔬菜，水分充足',                     '20千卡/100g',  20,  NOW(), NOW()),
(28, '菠菜',         NULL, '铁含量丰富的绿叶菜',                               '28千卡/100g',  28,  NOW(), NOW()),
(29, '芹菜',         NULL, '高纤维蔬菜，有助降压',                             '20千卡/100g',  20,  NOW(), NOW()),
(30, '西红柿',       NULL, '富含番茄红素，可蔬可果',                           '20千卡/100g',  20,  NOW(), NOW()),
(31, '黄瓜',         NULL, '低热量蔬菜，清爽可口',                             '16千卡/100g',  16,  NOW(), NOW()),
(32, '胡萝卜',       NULL, '富含β-胡萝卜素，护眼',                            '41千卡/100g',  41,  NOW(), NOW()),
(33, '洋葱',         NULL, '调味蔬菜，含硫化物',                               '40千卡/100g',  40,  NOW(), NOW()),
(34, '大蒜',         NULL, '调味食材，杀菌消炎',                               '149千卡/100g', 149, NOW(), NOW()),
(35, '生姜',         NULL, '调味去腥，温中散寒',                               '80千卡/100g',  80,  NOW(), NOW()),
(36, '青椒',         NULL, '富含维生素C的辣椒',                               '22千卡/100g',  22,  NOW(), NOW()),
(37, '茄子',         NULL, '紫色蔬菜，含花青素',                               '25千卡/100g',  25,  NOW(), NOW()),
(38, '西兰花',       NULL, '营养全面的十字花科蔬菜',                           '36千卡/100g',  36,  NOW(), NOW()),
(39, '花菜',         NULL, '十字花科蔬菜，口感清脆',                           '24千卡/100g',  24,  NOW(), NOW()),
(40, '蘑菇（鲜）',   NULL, '鲜蘑菇，菌类食材，鲜味十足',                         '24千卡/100g',  24,  NOW(), NOW()),
(41, '木耳（水发）', NULL, '水发木耳，食用菌，富含铁和胶质',                     '27千卡/100g',  27,  NOW(), NOW()),
-- 水果类
(42, '苹果',         NULL, '常见水果，富含果胶',                               '53千卡/100g',  53,  NOW(), NOW()),
(43, '香蕉',         NULL, '高钾水果，快速补充能量',                           '93千卡/100g',  93,  NOW(), NOW()),
(44, '橙子',         NULL, '富含维生素C的柑橘类水果',                         '48千卡/100g',  48,  NOW(), NOW()),
(45, '西瓜',         NULL, '夏季消暑水果，水分充足',                           '26千卡/100g',  26,  NOW(), NOW()),
(46, '葡萄',         NULL, '含多酚类抗氧化物质',                               '44千卡/100g',  44,  NOW(), NOW()),
(47, '草莓',         NULL, '浆果类水果，富含维生素C',                         '32千卡/100g',  32,  NOW(), NOW()),
(48, '柠檬',         NULL, '高酸水果，富含维生素C',                           '37千卡/100g',  37,  NOW(), NOW()),
-- 坚果
(49, '花生（生）',   NULL, '生花生米，富含不饱和脂肪酸',                       '567千卡/100g', 567, NOW(), NOW()),
(50, '核桃（干）',   NULL, '干核桃仁，补脑坚果，富含ω-3脂肪酸',                '654千卡/100g', 654, NOW(), NOW());

-- ============================================
-- 第四部分：插入饮食记录（diet_log）
-- 热量从 ingredient.calories_per_100g 快照写入
-- meal_type: 0=早餐 1=午餐 2=晚餐 3=加餐
-- ============================================

-- 用户1 今天的饮食记录
INSERT INTO diet_log (user_id, ingredient_id, ingredient_name, calories_per_100g, grams, meal_type, log_date, create_time) VALUES
-- 早餐
(1, 19, '牛奶',         54,  250, 0, CURDATE(), NOW()),
(1, 17, '鸡蛋',         147, 60,  0, CURDATE(), NOW()),
(1, 4,  '燕麦（干）',   389, 40,  0, CURDATE(), NOW()),
-- 午餐
(1, 1,  '大米（生）',   346, 80,  1, CURDATE(), NOW()),
(1, 7,  '鸡胸肉（生）', 133, 150, 1, CURDATE(), NOW()),
(1, 38, '西兰花',       36,  200, 1, CURDATE(), NOW()),
(1, 30, '西红柿',       20,  100, 1, CURDATE(), NOW()),
-- 晚餐
(1, 14, '带鱼（生）',   127, 120, 2, CURDATE(), NOW()),
(1, 25, '豆腐',         81,  150, 2, CURDATE(), NOW()),
(1, 27, '白菜',         20,  200, 2, CURDATE(), NOW()),
-- 加餐
(1, 42, '苹果',         53,  200, 3, CURDATE(), NOW());

-- 用户1 昨天的饮食记录
INSERT INTO diet_log (user_id, ingredient_id, ingredient_name, calories_per_100g, grams, meal_type, log_date, create_time) VALUES
-- 早餐
(1, 26, '豆浆',         31,  300, 0, CURDATE() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY),
(1, 17, '鸡蛋',         147, 60,  0, CURDATE() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY),
(1, 3,  '玉米（鲜）',   112, 200, 0, CURDATE() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY),
-- 午餐
(1, 1,  '大米（生）',   346, 75,  1, CURDATE() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY),
(1, 9,  '牛肉（生）',   106, 120, 1, CURDATE() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY),
(1, 28, '菠菜',         28,  150, 1, CURDATE() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY),
-- 晚餐
(1, 8,  '猪肉（生）',   143, 100, 2, CURDATE() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY),
(1, 6,  '土豆',         81,  150, 2, CURDATE() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY),
(1, 31, '黄瓜',         16,  100, 2, CURDATE() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY),
-- 加餐
(1, 43, '香蕉',         93,  150, 3, CURDATE() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY);

-- 用户2 今天的饮食记录
INSERT INTO diet_log (user_id, ingredient_id, ingredient_name, calories_per_100g, grams, meal_type, log_date, create_time) VALUES
-- 早餐
(2, 19, '牛奶',         54,  200, 0, CURDATE(), NOW()),
(2, 25, '豆腐',         81,  100, 0, CURDATE(), NOW()),
-- 午餐
(2, 1,  '大米（生）',   346, 100, 1, CURDATE(), NOW()),
(2, 12, '虾仁（生）',   48,  100, 1, CURDATE(), NOW()),
(2, 38, '西兰花',       36,  150, 1, CURDATE(), NOW()),
-- 晚餐
(2, 11, '排骨（生）',   278, 150, 2, CURDATE(), NOW()),
(2, 5,  '红薯',         99,  200, 2, CURDATE(), NOW()),
(2, 29, '芹菜',         20,  150, 2, CURDATE(), NOW()),
-- 加餐
(2, 44, '橙子',         48,  200, 3, CURDATE(), NOW());

-- ============================================
-- 第五部分：插入优惠券（商家ID改为1-6）
-- ============================================
-- 商家1：水果店
INSERT INTO voucher (id, shop_id, title, sub_title, pay_value, actual_value, type, status, validity_days, create_time, update_time) VALUES
(80010, 1, '水果店新客券', '新用户立减 5 元', 500, 0, 0, 1, 30, NOW(), NOW()),
(80011, 1, '水果店满减券', '满 50 元减 8 元', 800, 5000, 0, 1, 30, NOW(), NOW()),
(80012, 1, '【秒杀】水果店爆款券', '限量 50 张，先到先得', 2000, 5000, 1, 1, 30, NOW(), NOW());

INSERT INTO seckill_voucher (voucher_id, stock, begin_time, end_time, create_time, update_time) VALUES
(80012, 50, '2026-01-01 00:00:00', '2026-12-31 23:59:59', NOW(), NOW());

-- 商家2：杂货超市
INSERT INTO voucher (id, shop_id, title, sub_title, pay_value, actual_value, type, status, validity_days, create_time, update_time) VALUES
(80020, 2, '杂货超市满减券', '满 100 元减 20 元', 2000, 10000, 0, 1, 30, NOW(), NOW()),
(80021, 2, '杂货超市折扣券', '全场 8.8 折', 1200, 10000, 0, 1, 60, NOW(), NOW()),
(80022, 2, '【秒杀】杂货超市压测券', '大库存测试用，限 1000 张', 100, 0, 1, 1, 90, NOW(), NOW());

INSERT INTO seckill_voucher (voucher_id, stock, begin_time, end_time, create_time, update_time) VALUES
(80022, 1000, '2026-01-01 00:00:00', '2026-12-31 23:59:59', NOW(), NOW());

-- 商家3：蔬菜摊
INSERT INTO voucher (id, shop_id, title, sub_title, pay_value, actual_value, type, status, validity_days, create_time, update_time) VALUES
(80030, 3, '蔬菜摊满赠券', '满 30 元赠鸡蛋', 1000, 3000, 0, 1, 15, NOW(), NOW()),
(80031, 3, '蔬菜摊新客券', '新用户立减 3 元', 300, 0, 0, 1, 15, NOW(), NOW());

-- 商家4：肉铺
INSERT INTO voucher (id, shop_id, title, sub_title, pay_value, actual_value, type, status, validity_days, create_time, update_time) VALUES
(80040, 4, '肉铺满减券', '满 80 元减 15 元', 1500, 8000, 0, 1, 20, NOW(), NOW()),
(80041, 4, '【秒杀】肉铺限量特价券', '限量 30 张', 3000, 8000, 1, 1, 60, NOW(), NOW());

INSERT INTO seckill_voucher (voucher_id, stock, begin_time, end_time, create_time, update_time) VALUES
(80041, 30, '2026-01-01 00:00:00', '2026-12-31 23:59:59', NOW(), NOW());

-- 商家5：海鲜店
INSERT INTO voucher (id, shop_id, title, sub_title, pay_value, actual_value, type, status, validity_days, create_time, update_time) VALUES
(80050, 5, '海鲜店满减券', '满 100 元减 25 元', 2500, 10000, 0, 1, 30, NOW(), NOW()),
(80051, 5, '【秒杀】海鲜店鲜活券', '限量 20 张', 5000, 10000, 1, 1, 7, NOW(), NOW());

INSERT INTO seckill_voucher (voucher_id, stock, begin_time, end_time, create_time, update_time) VALUES
(80051, 20, '2026-01-01 00:00:00', '2026-12-31 23:59:59', NOW(), NOW());

-- 商家6：烘焙坊
INSERT INTO voucher (id, shop_id, title, sub_title, pay_value, actual_value, type, status, validity_days, create_time, update_time) VALUES
(80060, 6, '烘焙坊下午茶券', '下午茶套餐立减 12 元', 1200, 6000, 0, 1, 20, NOW(), NOW()),
(80061, 6, '烘焙坊生日券', '生日月专享 8 折', 800, 5000, 0, 1, 30, NOW(), NOW());

-- ============================================
-- 第六部分：验证数据
-- ============================================
SELECT '=== 商家列表 ===' AS info;
SELECT id, name, address, score FROM merchant ORDER BY id;

SELECT '=== 食材列表 ===' AS info;
SELECT id, name, nutrition_value, calories_per_100g FROM ingredient ORDER BY id;

SELECT '=== 饮食记录 ===' AS info;
SELECT id, user_id, ingredient_name, calories_per_100g, grams, meal_type, log_date FROM diet_log ORDER BY log_date DESC, id;

SELECT '=== 优惠券列表 ===' AS info;
SELECT id, shop_id, title, type, validity_days FROM voucher ORDER BY id;

SELECT '=== 秒杀券列表 ===' AS info;
SELECT voucher_id, stock FROM seckill_voucher ORDER BY voucher_id;

SELECT '=== 订单数量 ===' AS info;
SELECT COUNT(*) AS order_count FROM voucher_order;

SELECT '=== 商品数量 ===' AS info;
SELECT COUNT(*) AS product_count FROM product;