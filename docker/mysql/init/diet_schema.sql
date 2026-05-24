-- diet-service 建表：今日食谱记录
CREATE TABLE IF NOT EXISTS `diet_log` (
  `id`               bigint(20)      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`          bigint(20)      NOT NULL                COMMENT '用户ID',
  `ingredient_id`    bigint(20)      NOT NULL                COMMENT '食材ID',
  `ingredient_name`  varchar(100)    NOT NULL                COMMENT '食材名称（冗余）',
  `calories_per_100g` decimal(8,2)   NOT NULL DEFAULT 0      COMMENT '每100g热量(千卡)',
  `grams`            decimal(8,2)    NOT NULL                COMMENT '摄入克数',
  `meal_type`        tinyint(1)      NOT NULL DEFAULT 0      COMMENT '0早餐 1午餐 2晚餐 3加餐',
  `log_date`         date            NOT NULL                COMMENT '记录日期',
  `create_time`      datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_date` (`user_id`, `log_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='今日食谱记录';
