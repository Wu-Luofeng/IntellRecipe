USE intell_recipe;

-- DeadLetter.java @TableName("tb_dead_letter") — safe to re-run on MySQL 5.7
CREATE TABLE IF NOT EXISTS `tb_dead_letter` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `message_id` varchar(100) DEFAULT NULL COMMENT '消息ID（MQ的messageId）',
  `exchange` varchar(100) DEFAULT NULL COMMENT '死信来源交换机',
  `routing_key` varchar(100) DEFAULT NULL COMMENT '死信来源路由键',
  `queue_name` varchar(100) DEFAULT NULL COMMENT '死信所在队列',
  `content` text NOT NULL COMMENT '死信消息体内容（JSON格式）',
  `reason` varchar(500) DEFAULT NULL COMMENT '死信原因（异常堆栈或描述）',
  `status` tinyint(1) DEFAULT '0' COMMENT '处理状态：0-未处理，1-已处理，2-处理失败',
  `retry_count` int(11) DEFAULT '0' COMMENT '重试次数',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '死信发生时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_message_id` (`message_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='死信消息兜底告警表';
