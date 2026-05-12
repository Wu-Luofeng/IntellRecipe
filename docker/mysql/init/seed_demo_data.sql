-- Demo / test data: real-style ingredients, merchants, products, vouchers.
-- Safe ID ranges: merchants 501–503, users via phone, ingredients 60001+, products 70001+, vouchers 80001+.
-- Run:  cat docker/mysql/init/seed_demo_data.sql | docker exec -i intellrecipe-mysql mysql -uroot -pYOUR_PASSWORD intell_recipe
-- Re-run: REPLACE / DELETE scoped to seed IDs only.
-- 登录：user-service 为「手机号 + 短信验证码」，password 可为 NULL；测试手机 13812345001 等需在 Redis 里配合验证码流程。

USE intell_recipe;

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- Merchants (REPLACE by id so re-run is idempotent)
-- ---------------------------------------------------------------------------
REPLACE INTO `merchant` (`id`, `name`, `address`, `phone`, `image`, `score`, `description`, `open_time`, `deleted`)
VALUES
(501, '江南鲜生市集', '杭州市西湖区文三路 168 号', '0571-88880001', 'https://picsum.photos/seed/merchant501/400/300', 4.8, '本地鲜蔬、肉禽水产一站式，支持半成品净菜。', '07:30-21:30', 0),
(502, '蜀味坊半成品', '成都市锦江区春熙路东段 88 号', '028-88880002', 'https://picsum.photos/seed/merchant502/400/300', 4.6, '川味调料与火锅底料、净菜套餐，懒人快手菜。', '09:00-22:00', 0),
(503, '粮油平价店', '广州市天河区体育西路 200 号', '020-88880003', 'https://picsum.photos/seed/merchant503/400/300', 4.3, '米面粮油、南北干货、基础调味品特价专区。', '08:00-20:00', 0);

-- ---------------------------------------------------------------------------
-- Users (phone UNIQUE — ON DUPLICATE KEY UPDATE keeps row stable)
-- ---------------------------------------------------------------------------
INSERT INTO `user` (`phone`, `nickname`, `password`, `avatar`, `status`, `deleted`)
VALUES
('13812345001', '测试用户_阿明', NULL, 'https://picsum.photos/seed/u1/200/200', 0, 0),
('13812345002', '测试用户_小禾', NULL, 'https://picsum.photos/seed/u2/200/200', 0, 0),
('13812345003', '测试用户_老周', NULL, 'https://picsum.photos/seed/u3/200/200', 0, 0)
ON DUPLICATE KEY UPDATE `nickname` = VALUES(`nickname`), `update_time` = CURRENT_TIMESTAMP;

-- ---------------------------------------------------------------------------
-- Ingredients (id fixed; name UNIQUE — REPLACE clears old seed row if re-run)
-- ---------------------------------------------------------------------------
REPLACE INTO `ingredient` (`id`, `name`, `image`, `description`, `nutrition_value`, `deleted`) VALUES
(60001, '鸡蛋', 'https://picsum.photos/seed/egg/200/200', '鲜鸡蛋，蛋白优质来源。', '144千卡/100g', 0),
(60002, '西红柿', 'https://picsum.photos/seed/tomato/200/200', '沙瓤番茄，适合炒制出汁。', '18千卡/100g', 0),
(60003, '土豆', 'https://picsum.photos/seed/potato/200/200', '黄心土豆，炖煮软糯。', '77千卡/100g', 0),
(60004, '青椒', 'https://picsum.photos/seed/pepper/200/200', '薄皮青椒，微辣清香。', '22千卡/100g', 0),
(60005, '大蒜', 'https://picsum.photos/seed/garlic/200/200', '独头蒜，辛香浓郁。', '126千卡/100g', 0),
(60006, '生姜', 'https://picsum.photos/seed/ginger/200/200', '老姜，去腥增香。', '41千卡/100g', 0),
(60007, '小葱', 'https://picsum.photos/seed/scallion/200/200', '细香葱，点缀提味。', '27千卡/100g', 0),
(60008, '五花肉', 'https://picsum.photos/seed/porkbelly/200/200', '三层五花，红烧东坡肉常用。', '395千卡/100g', 0),
(60009, '牛腩', 'https://picsum.photos/seed/brisket/200/200', '牛腩块，适合番茄牛腩。', '250千卡/100g', 0),
(60010, '鸡翅中', 'https://picsum.photos/seed/wing/200/200', '翅中，可乐鸡翅、奥尔良烤翅。', '194千卡/100g', 0),
(60011, '虾仁', 'https://picsum.photos/seed/shrimp/200/200', '青虾仁，已去虾线。', '87千卡/100g', 0),
(60012, '北豆腐', 'https://picsum.photos/seed/tofu/200/200', '卤水豆腐，质地紧实。', '81千卡/100g', 0),
(60013, '鲜香菇', 'https://picsum.photos/seed/shiitake/200/200', '菌盖厚实，煲汤小炒皆宜。', '22千卡/100g', 0),
(60014, '干木耳', 'https://picsum.photos/seed/woodear/200/200', '东北秋木耳，泡发后爽脆。', '21千卡/100g', 0),
(60015, '五常大米', 'https://picsum.photos/seed/rice/200/200', '稻花香二号，一年一季。', '346千卡/100g', 0),
(60016, '生抽', 'https://picsum.photos/seed/soysauce1/200/200', '酿造生抽，提鲜上色轻。', '63千卡/100ml', 0),
(60017, '老抽', 'https://picsum.photos/seed/soysauce2/200/200', '老抽上色，红烧必备。', '63千卡/100ml', 0),
(60018, '料酒', 'https://picsum.photos/seed/winecook/200/200', '烹饪黄酒，去腥增香。', '114千卡/100ml', 0),
(60019, '蚝油', 'https://picsum.photos/seed/oyster/200/200', '鲜蚝熬制，勾芡拌馅。', '111千卡/100g', 0),
(60020, '花生油', 'https://picsum.photos/seed/peanutoil/200/200', '压榨一级，烟点高适合煎炒。', '884千卡/100g', 0),
(60021, '食用盐', 'https://picsum.photos/seed/salt/200/200', '加碘精制盐，调味基础。', '0千卡/100g', 0),
(60022, '白砂糖', 'https://picsum.photos/seed/sugar/200/200', '蔗糖，炒糖色、烘焙。', '400千卡/100g', 0),
(60023, '花椒', 'https://picsum.photos/seed/peppercorn/200/200', '汉源红花椒，麻香突出。', '298千卡/100g', 0),
(60024, '干辣椒', 'https://picsum.photos/seed/chili/200/200', '二荆条干辣椒段。', '324千卡/100g', 0),
(60025, '八角', 'https://picsum.photos/seed/star/200/200', '大料，卤肉炖汤常用。', '195千卡/100g', 0),
(60026, '桂皮', 'https://picsum.photos/seed/cinnamon/200/200', '肉桂卷，卤水香料。', '247千卡/100g', 0),
(60027, '香菜', 'https://picsum.photos/seed/cilantro/200/200', '芫荽，凉拌汤面点缀。', '23千卡/100g', 0),
(60028, '芹菜', 'https://picsum.photos/seed/celery/200/200', '西芹，高纤维低热量。', '16千卡/100g', 0),
(60029, '胡萝卜', 'https://picsum.photos/seed/carrot/200/200', '水果胡萝卜，清炒炖煮。', '41千卡/100g', 0),
(60030, '洋葱', 'https://picsum.photos/seed/onion/200/200', '紫皮洋葱，炒软出甜。', '40千卡/100g', 0),
(60031, '黄瓜', 'https://picsum.photos/seed/cucumber/200/200', '刺黄瓜，凉拌拍黄瓜。', '16千卡/100g', 0),
(60032, '茄子', 'https://picsum.photos/seed/eggplant/200/200', '长茄，鱼香茄子、烧茄子。', '25千卡/100g', 0),
(60033, '莲藕', 'https://picsum.photos/seed/lotus/200/200', '脆藕，清炒糯米藕。', '47千卡/100g', 0),
(60034, '菠菜', 'https://picsum.photos/seed/spinach/200/200', '小叶菠菜，焯水去草酸。', '24千卡/100g', 0),
(60035, '大白菜', 'https://picsum.photos/seed/cabbage/200/200', '黄心大白菜，炖豆腐粉条。', '17千卡/100g', 0),
(60036, '金针菇', 'https://picsum.photos/seed/enoki/200/200', '菌柄细长，火锅必备。', '26千卡/100g', 0),
(60037, '绿豆芽', 'https://picsum.photos/seed/bean/200/200', '短豆芽，醋溜豆芽。', '18千卡/100g', 0),
(60038, '小米辣', 'https://picsum.photos/seed/birdchili/200/200', '鲜小米辣，极辣慎用。', '40千卡/100g', 0),
(60039, '郫县豆瓣酱', 'https://picsum.photos/seed/douban/200/200', '红油豆瓣，回锅肉灵魂。', '178千卡/100g', 0),
(60040, '火锅底料', 'https://picsum.photos/seed/hotpot/200/200', '牛油麻辣底料，半块煮一锅。', '约450千卡/100g', 0),
(60041, '挂面', 'https://picsum.photos/seed/noodle/200/200', '细圆挂面，速食汤面。', '348千卡/100g', 0),
(60042, '速冻水饺', 'https://picsum.photos/seed/dumpling/200/200', '猪肉白菜馅，无需解冻。', '约250千卡/100g', 0),
(60043, '培根', 'https://picsum.photos/seed/bacon/200/200', '烟熏培根片，早餐煎烤。', '405千卡/100g', 0),
(60044, '黄油', 'https://picsum.photos/seed/butter/200/200', '无盐动物黄油，煎牛排烘焙。', '717千卡/100g', 0),
(60045, '淡奶油', 'https://picsum.photos/seed/cream/200/200', '动物淡奶油 35% 脂肪，裱花甜品。', '350千卡/100ml', 0);

-- ---------------------------------------------------------------------------
-- Products (merchant 501 / 502 / 503)
-- ---------------------------------------------------------------------------
REPLACE INTO `product` (`id`, `merchant_id`, `name`, `price`, `image`, `description`, `weight`, `unit`, `status`, `deleted`) VALUES
(70001, 501, '西红柿炒蛋净菜包（2人份）', 18.90, 'https://picsum.photos/seed/p70001/400/300', '含洗净切块西红柿、打散蛋液、小葱，附步骤卡。', 650.00, 'g', 1, 0),
(70002, 501, '清炒时蔬三拼', 12.50, 'https://picsum.photos/seed/p70002/400/300', '青椒、土豆、胡萝卜净菜组合，配蒜末。', 500.00, 'g', 1, 0),
(70003, 502, '麻婆豆腐套装', 22.00, 'https://picsum.photos/seed/p70003/400/300', '北豆腐、牛肉末、郫县豆瓣酱、花椒、青蒜，配菜谱。', 800.00, 'g', 1, 0),
(70004, 502, '番茄牛腩半成品', 48.00, 'https://picsum.photos/seed/p70004/400/300', '牛腩块、番茄块、姜片、八角桂皮组合。', 900.00, 'g', 1, 0),
(70005, 503, '五常大米 5kg', 59.90, 'https://picsum.photos/seed/p70005/400/300', '真空包装，产地黑龙江五常。', 5000.00, 'g', 1, 0),
(70006, 503, '基础调味四件套', 29.90, 'https://picsum.photos/seed/p70006/400/300', '生抽、老抽、料酒、蚝油小瓶装组合。', 1200.00, 'ml', 1, 0),
(70007, 501, '菌菇火锅蔬菜包', 26.00, 'https://picsum.photos/seed/p70007/400/300', '鲜香菇、金针菇、菠菜、莲藕片净菜。', 700.00, 'g', 1, 0),
(70008, 502, '川味油碟蘸料包', 9.90, 'https://picsum.photos/seed/p70008/400/300', '蒜蓉、小米辣、香油小袋分装。', 200.00, 'g', 1, 0);

-- ---------------------------------------------------------------------------
-- Product ↔ Ingredient (delete seed links then insert)
-- ---------------------------------------------------------------------------
DELETE FROM `product_ingredient` WHERE `product_id` BETWEEN 70001 AND 70008;
INSERT INTO `product_ingredient` (`product_id`, `ingredient_id`, `deleted`) VALUES
(70001, 60002, 0), (70001, 60001, 0), (70001, 60007, 0), (70001, 60016, 0), (70001, 60021, 0),
(70002, 60004, 0), (70002, 60003, 0), (70002, 60029, 0), (70002, 60005, 0),
(70003, 60012, 0), (70003, 60039, 0), (70003, 60023, 0), (70003, 60016, 0), (70003, 60020, 0),
(70004, 60009, 0), (70004, 60002, 0), (70004, 60006, 0), (70004, 60025, 0), (70004, 60026, 0),
(70007, 60013, 0), (70007, 60036, 0), (70007, 60034, 0), (70007, 60033, 0),
(70008, 60005, 0), (70008, 60038, 0), (70008, 60020, 0);

-- ---------------------------------------------------------------------------
-- Vouchers: 普通券 + 秒杀券（pay_value / actual_value 单位：分）
-- ---------------------------------------------------------------------------
DELETE FROM `seckill_voucher` WHERE `voucher_id` IN (80002);
DELETE FROM `voucher` WHERE `id` IN (80001, 80002);

INSERT INTO `voucher` (`id`, `shop_id`, `title`, `sub_title`, `pay_value`, `actual_value`, `type`, `status`) VALUES
(80001, 501, '鲜生市集满减券', '满 88 元可用', 1000, 8800, 0, 1),
(80002, 502, '蜀味坊秒杀抵扣券', '限量 100 张 · 番茄牛腩套餐专用', 1500, 3000, 1, 1);

INSERT INTO `seckill_voucher` (`voucher_id`, `stock`, `begin_time`, `end_time`) VALUES
(80002, 100, '2026-05-01 00:00:00', '2026-12-31 23:59:59');

-- ---------------------------------------------------------------------------
-- Sample voucher order (bigint id — demo snowflake-style)
-- ---------------------------------------------------------------------------
DELETE FROM `voucher_order` WHERE `id` = 1905120000000000001;
INSERT INTO `voucher_order` (`id`, `user_id`, `voucher_id`, `pay_type`, `status`, `pay_time`)
SELECT 1905120000000000001, `id`, 80001, 1, 2, NOW() FROM `user` WHERE `phone` = '13812345001' LIMIT 1;

SELECT 'seed_demo_data.sql done' AS result;
SELECT COUNT(*) AS ingredient_cnt FROM `ingredient` WHERE `id` BETWEEN 60001 AND 60045;
SELECT COUNT(*) AS product_cnt FROM `product` WHERE `id` BETWEEN 70001 AND 70008;
