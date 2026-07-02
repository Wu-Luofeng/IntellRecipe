-- 设置字符集
SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;
SET character_set_connection = utf8mb4;
SET character_set_database = utf8mb4;
SET character_set_results = utf8mb4;

-- 重置优惠券数据
USE intell_recipe;

-- 清空旧数据
DELETE FROM voucher_order;
DELETE FROM seckill_voucher;
DELETE FROM voucher;

-- 重置自增ID
ALTER TABLE voucher AUTO_INCREMENT = 80010;

-- 插入新的优惠券数据（全部设置有效期）
-- 商家501：鲜生市集
INSERT INTO voucher (id, shop_id, title, sub_title, pay_value, actual_value, type, status, validity_days, create_time, update_time) VALUES
(80010, 501, '鲜生市集新客专享券', '新用户首单立减 5 元', 500, 0, 0, 1, 30, NOW(), NOW()),
(80011, 501, '鲜生市集满减券', '满 50 元减 8 元', 800, 5000, 0, 1, 30, NOW(), NOW()),
(80012, 501, '鲜生市集折扣券', '全场 8.8 折', 1200, 10000, 0, 1, 60, NOW(), NOW()),
(80013, 501, '鲜生市集满赠券', '满 100 元赠饮品', 1500, 10000, 0, 1, 45, NOW(), NOW()),
(80014, 501, '鲜生市集周末专享券', '周末满 80 减 15', 1500, 8000, 0, 1, 15, NOW(), NOW());

-- 商家501的秒杀券
INSERT INTO voucher (id, shop_id, title, sub_title, pay_value, actual_value, type, status, validity_days, create_time, update_time) VALUES
(80020, 501, '【秒杀】鲜生市集爆款券', '限量 50 张，先到先得', 2000, 5000, 1, 1, 30, NOW(), NOW()),
(80023, 501, '【秒杀】高并发压测券', '大库存测试用，限 1000 张', 100, 0, 1, 1, 90, NOW(), NOW());

-- 秒杀券扩展信息
INSERT INTO seckill_voucher (voucher_id, stock, begin_time, end_time, create_time, update_time) VALUES
(80020, 50, '2026-05-16 00:00:00', '2026-12-31 23:59:59', NOW(), NOW()),
(80023, 1000, '2026-05-16 00:00:00', '2026-12-31 23:59:59', NOW(), NOW());

-- 商家502：美味餐厅
INSERT INTO voucher (id, shop_id, title, sub_title, pay_value, actual_value, type, status, validity_days, create_time, update_time) VALUES
(80030, 502, '美味餐厅满减券', '满 100 元减 20 元', 2000, 10000, 0, 1, 30, NOW(), NOW()),
(80031, 502, '美味餐厅新客券', '新用户立减 10 元', 1000, 0, 0, 1, 15, NOW(), NOW()),
(80032, 502, '【秒杀】美味餐厅招牌菜券', '限量 30 张', 3000, 8000, 1, 1, 60, NOW(), NOW());

INSERT INTO seckill_voucher (voucher_id, stock, begin_time, end_time, create_time, update_time) VALUES
(80032, 30, '2026-05-16 00:00:00', '2026-12-31 23:59:59', NOW(), NOW());

-- 商家503：甜品店
INSERT INTO voucher (id, shop_id, title, sub_title, pay_value, actual_value, type, status, validity_days, create_time, update_time) VALUES
(80040, 503, '甜品店下午茶券', '下午茶套餐立减 12 元', 1200, 6000, 0, 1, 20, NOW(), NOW()),
(80041, 503, '甜品店生日券', '生日月专享 8 折', 800, 5000, 0, 1, 30, NOW(), NOW()),
(80042, 503, '【秒杀】甜品店限量甜品券', '每日限量 20 张', 500, 3000, 1, 1, 7, NOW(), NOW());

INSERT INTO seckill_voucher (voucher_id, stock, begin_time, end_time, create_time, update_time) VALUES
(80042, 20, '2026-05-16 00:00:00', '2026-12-31 23:59:59', NOW(), NOW());

-- 验证数据
SELECT '=== 优惠券列表 ===' AS info;
SELECT id, shop_id, title, type, status, validity_days FROM voucher ORDER BY id;
SELECT '=== 秒杀券列表 ===' AS info;
SELECT voucher_id, stock FROM seckill_voucher ORDER BY voucher_id;
SELECT '=== 订单数量 ===' AS info;
SELECT COUNT(*) AS order_count FROM voucher_order;