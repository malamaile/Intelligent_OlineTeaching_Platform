-- ============================================================
-- 迁移脚本：teaching_resource 表新增 chapter_id 字段
-- 用途：将教学资源关联到具体课程章节
USE iotp;

ALTER TABLE teaching_resource
    ADD COLUMN chapter_id BIGINT DEFAULT NULL COMMENT '关联章节ID（可为空，为空表示独立资源）',
    ADD KEY idx_chapter_id (chapter_id);
