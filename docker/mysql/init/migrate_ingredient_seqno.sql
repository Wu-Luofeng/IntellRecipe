-- 食材表新增序列号字段
-- 规则：从1开始连续递增，删除后自动重排不跳号

ALTER TABLE `ingredient` ADD COLUMN `seq_no` int(11) DEFAULT NULL COMMENT '序列号（从1递增，删除后重排）' AFTER `calories_per100g`;

-- 初始化：按 id 升序分配连续序列号 1,2,3...
SET @row_num = 0;
UPDATE `ingredient` SET `seq_no` = (@row_num := @row_num + 1) ORDER BY `id` ASC;

-- 添加唯一索引，防止序列号重复
ALTER TABLE `ingredient` ADD UNIQUE INDEX `uk_seq_no` (`seq_no`);