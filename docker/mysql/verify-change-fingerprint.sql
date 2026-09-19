USE `intell_recipe`;

-- ============ 验证 1：语法 + 无变更时的稳定性 ============
SELECT '=== 1. 真实表的指纹（连查两次，必须相同）===' AS step;
SELECT COALESCE(BIT_XOR(CRC32(CONCAT(id, '-', IFNULL(update_time, '')))), 0) AS fp_1st FROM ingredient;
SELECT COALESCE(BIT_XOR(CRC32(CONCAT(id, '-', IFNULL(update_time, '')))), 0) AS fp_2nd FROM ingredient;

-- ============ 验证 2：变更敏感性（临时表，不影响业务数据）============
DROP TEMPORARY TABLE IF EXISTS fp_test;
CREATE TEMPORARY TABLE fp_test (
  id BIGINT PRIMARY KEY,
  update_time DATETIME
) ENGINE=InnoDB;

INSERT INTO fp_test VALUES (1, '2026-01-01 00:00:00'), (2, '2026-01-01 00:00:00'), (3, '2026-01-01 00:00:00');

SELECT '=== 2. 基线指纹 ===' AS step;
SELECT COALESCE(BIT_XOR(CRC32(CONCAT(id, '-', IFNULL(update_time, '')))), 0) AS fp_baseline FROM fp_test;

SELECT '=== 3. 改一条记录的 update_time 后（期望与基线不同）===' AS step;
UPDATE fp_test SET update_time = '2026-01-01 00:00:01' WHERE id = 1;
SELECT COALESCE(BIT_XOR(CRC32(CONCAT(id, '-', IFNULL(update_time, '')))), 0) AS fp_after_update FROM fp_test;

SELECT '=== 4. 新增一条后（期望再次变化）===' AS step;
INSERT INTO fp_test VALUES (4, '2026-01-01 00:00:00');
SELECT COALESCE(BIT_XOR(CRC32(CONCAT(id, '-', IFNULL(update_time, '')))), 0) AS fp_after_insert FROM fp_test;

SELECT '=== 5. 物理删除一条后（期望再次变化）===' AS step;
DELETE FROM fp_test WHERE id = 4;
SELECT COALESCE(BIT_XOR(CRC32(CONCAT(id, '-', IFNULL(update_time, '')))), 0) AS fp_after_delete FROM fp_test;

SELECT '=== 6. 空表（期望 0，不能是 NULL）===' AS step;
SELECT COALESCE(BIT_XOR(CRC32(CONCAT(id, '-', IFNULL(update_time, '')))), 0) AS fp_empty
FROM fp_test WHERE 1 = 0;

SELECT '=== 7. update_time 为 NULL 时也不报错 ===' AS step;
INSERT INTO fp_test VALUES (9, NULL);
SELECT COALESCE(BIT_XOR(CRC32(CONCAT(id, '-', IFNULL(update_time, '')))), 0) AS fp_null_ts FROM fp_test;

DROP TEMPORARY TABLE fp_test;
SELECT '=== 完成 ===' AS step;
