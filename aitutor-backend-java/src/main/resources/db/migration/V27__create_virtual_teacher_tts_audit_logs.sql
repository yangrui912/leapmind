-- V27: M8 虚拟教师 TTS 审计日志表
-- 该表在实体与 Mapper 中已使用，但缺少对应迁移脚本，导致
-- "Table 'leapmind.virtual_teacher_tts_audit_logs' doesn't exist"。
CREATE TABLE IF NOT EXISTS `virtual_teacher_tts_audit_logs` (
  `id`           bigint       NOT NULL AUTO_INCREMENT,
  `user_id`      bigint       DEFAULT NULL COMMENT '用户ID',
  `course_id`    varchar(64)  DEFAULT NULL COMMENT '课程ID',
  `voice_type`   varchar(64)  DEFAULT NULL COMMENT '音色',
  `text_length`  int          DEFAULT 0 COMMENT '文本字数',
  `cache_hit`    tinyint(1)   DEFAULT 0 COMMENT '是否命中缓存',
  `audio_size`   bigint       DEFAULT 0 COMMENT '音频字节数',
  `latency_ms`   bigint       DEFAULT 0 COMMENT '合成耗时(ms)',
  `status`       varchar(20)  DEFAULT 'SUCCESS' COMMENT '状态',
  `error_message` varchar(500) DEFAULT NULL COMMENT '失败原因',
  `created_at`   datetime     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_vt_audit_user` (`user_id`),
  KEY `idx_vt_audit_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='虚拟教师 TTS 审计日志';
