-- ============================================================
-- 智能化在线教学支持服务平台（IOTP）v1.0
-- 数据库初始化脚本
-- Database: MySQL 8.0+
-- Author: 杨雨洁
-- Date: 2026-06-18
-- ============================================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS iotp DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE iotp;

-- ============================================================
-- 1. 系统基础模块（角色、用户、院系、班级、学期）
-- ============================================================

-- 1.1 系统角色表
DROP TABLE IF EXISTS sys_role;
CREATE TABLE sys_role (
    id          BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '角色ID',
    role_code   VARCHAR(32)     NOT NULL                 COMMENT '角色编码：STUDENT-学生, TEACHER-教师, ADMIN-管理员',
    role_name   VARCHAR(64)     NOT NULL                 COMMENT '角色名称',
    description VARCHAR(255)    DEFAULT NULL             COMMENT '角色描述',
    is_deleted  TINYINT(1)      NOT NULL DEFAULT 0       COMMENT '逻辑删除：0-未删除, 1-已删除',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统角色表';

-- 1.2 院系表
DROP TABLE IF EXISTS sys_department;
CREATE TABLE sys_department (
    id          BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '院系ID',
    dept_name   VARCHAR(128)    NOT NULL                 COMMENT '院系名称',
    dept_code   VARCHAR(32)     NOT NULL                 COMMENT '院系编码',
    description VARCHAR(255)    DEFAULT NULL             COMMENT '院系描述',
    sort_order  INT             NOT NULL DEFAULT 0       COMMENT '排序号',
    is_deleted  TINYINT(1)      NOT NULL DEFAULT 0       COMMENT '逻辑删除',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_dept_code (dept_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='院系表';

-- 1.3 班级表
DROP TABLE IF EXISTS sys_class;
CREATE TABLE sys_class (
    id              BIGINT      NOT NULL AUTO_INCREMENT  COMMENT '班级ID',
    class_name      VARCHAR(128) NOT NULL                COMMENT '班级名称',
    class_code      VARCHAR(32) NOT NULL                 COMMENT '班级编码',
    department_id   BIGINT      NOT NULL                 COMMENT '所属院系ID',
    grade           VARCHAR(16) NOT NULL                 COMMENT '年级，如：2024',
    is_deleted      TINYINT(1)  NOT NULL DEFAULT 0       COMMENT '逻辑删除',
    create_time     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_class_code (class_code),
    KEY idx_department_id (department_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='班级表';

-- 1.4 学期表
DROP TABLE IF EXISTS sys_semester;
CREATE TABLE sys_semester (
    id              BIGINT      NOT NULL AUTO_INCREMENT  COMMENT '学期ID',
    semester_name   VARCHAR(64) NOT NULL                 COMMENT '学期名称，如：2024-2025-1',
    start_date      DATE        NOT NULL                 COMMENT '学期开始日期',
    end_date        DATE        NOT NULL                 COMMENT '学期结束日期',
    is_current      TINYINT(1)  NOT NULL DEFAULT 0       COMMENT '是否当前学期：0-否, 1-是',
    is_deleted      TINYINT(1)  NOT NULL DEFAULT 0       COMMENT '逻辑删除',
    create_time     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学期表';

-- 1.5 系统用户表（统一存储学生、教师、管理员账号）
DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '用户ID',
    username        VARCHAR(64)     NOT NULL                 COMMENT '登录账号',
    password        VARCHAR(256)    NOT NULL                 COMMENT '加密后的登录密码',
    real_name       VARCHAR(64)     NOT NULL                 COMMENT '真实姓名',
    email           VARCHAR(128)    DEFAULT NULL             COMMENT '邮箱',
    phone           VARCHAR(32)     DEFAULT NULL             COMMENT '联系电话',
    avatar          VARCHAR(512)    DEFAULT NULL             COMMENT '头像URL',
    role_id         BIGINT          NOT NULL                 COMMENT '角色ID，关联sys_role',
    department_id   BIGINT          DEFAULT NULL             COMMENT '所属院系ID（教师、学生）',
    class_id        BIGINT          DEFAULT NULL             COMMENT '所属班级ID（学生）',
    status          TINYINT(1)      NOT NULL DEFAULT 1       COMMENT '账号状态：0-禁用, 1-启用',
    login_fail_count INT            NOT NULL DEFAULT 0       COMMENT '连续登录失败次数',
    lock_until_time  DATETIME       DEFAULT NULL             COMMENT '账号锁定截止时间',
    last_login_time  DATETIME       DEFAULT NULL             COMMENT '最后登录时间',
    last_login_ip   VARCHAR(64)     DEFAULT NULL             COMMENT '最后登录IP',
    is_deleted      TINYINT(1)      NOT NULL DEFAULT 0       COMMENT '逻辑删除',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    KEY idx_role_id (role_id),
    KEY idx_department_id (department_id),
    KEY idx_class_id (class_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表';

-- 1.6 密码重置记录表
DROP TABLE IF EXISTS sys_password_reset;
CREATE TABLE sys_password_reset (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '记录ID',
    user_id         BIGINT          NOT NULL                 COMMENT '用户ID',
    reset_token     VARCHAR(128)    NOT NULL                 COMMENT '重置令牌',
    expire_time     DATETIME        NOT NULL                 COMMENT '令牌过期时间',
    is_used         TINYINT(1)      NOT NULL DEFAULT 0       COMMENT '是否已使用',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_reset_token (reset_token),
    KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='密码重置记录表';


-- ============================================================
-- 2. 课程计划模块
-- ============================================================

-- 2.1 课程基本信息表
DROP TABLE IF EXISTS course;
CREATE TABLE course (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '课程ID',
    course_name     VARCHAR(128)    NOT NULL                 COMMENT '课程名称',
    course_code     VARCHAR(32)     NOT NULL                 COMMENT '课程编码',
    description     TEXT            DEFAULT NULL             COMMENT '课程简介',
    cover_image     VARCHAR(512)    DEFAULT NULL             COMMENT '课程封面图URL',
    department_id   BIGINT          NOT NULL                 COMMENT '所属院系ID',
    teacher_id      BIGINT          NOT NULL                 COMMENT '创建教师ID',
    -- 审核相关
    audit_status    VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT '审核状态：PENDING-待审核, APPROVED-审核通过, REJECTED-审核驳回',
    audit_admin_id  BIGINT          DEFAULT NULL             COMMENT '审核管理员ID',
    audit_comment   VARCHAR(512)    DEFAULT NULL             COMMENT '审核意见/驳回原因',
    audit_time      DATETIME        DEFAULT NULL             COMMENT '审核时间',
    status          TINYINT(1)      NOT NULL DEFAULT 1       COMMENT '课程状态：0-停用, 1-启用',
    is_deleted      TINYINT(1)      NOT NULL DEFAULT 0       COMMENT '逻辑删除',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_course_code (course_code),
    KEY idx_department_id (department_id),
    KEY idx_teacher_id (teacher_id),
    KEY idx_audit_status (audit_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程基本信息表';

-- 2.2 课程章节课时表
DROP TABLE IF EXISTS course_chapter;
CREATE TABLE course_chapter (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '章节ID',
    course_id       BIGINT          NOT NULL                 COMMENT '所属课程ID',
    chapter_name    VARCHAR(256)    NOT NULL                 COMMENT '章节名称',
    chapter_order   INT             NOT NULL DEFAULT 1       COMMENT '章节排序号',
    -- 视频资源
    video_url       VARCHAR(512)    DEFAULT NULL             COMMENT '教学视频URL',
    video_duration  INT             DEFAULT 0                COMMENT '视频时长（秒）',
    -- 课件内容
    content_text    LONGTEXT        DEFAULT NULL             COMMENT '章节文本内容（富文本）',
    attachment_url  VARCHAR(512)    DEFAULT NULL             COMMENT '附件URL（课件PDF等）',
    is_deleted      TINYINT(1)      NOT NULL DEFAULT 0       COMMENT '逻辑删除',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_course_id (course_id),
    KEY idx_course_order (course_id, chapter_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程章节课时表';

-- 2.3 开课计划表（教师提交开课申请）
DROP TABLE IF EXISTS course_plan;
CREATE TABLE course_plan (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '开课计划ID',
    course_id       BIGINT          NOT NULL                 COMMENT '课程ID',
    semester_id     BIGINT          NOT NULL                 COMMENT '学期ID',
    class_id        BIGINT          NOT NULL                 COMMENT '授课班级ID',
    teacher_id      BIGINT          NOT NULL                 COMMENT '授课教师ID',
    schedule_info   VARCHAR(512)    DEFAULT NULL             COMMENT '授课周期及课时安排描述',
    -- 审核相关
    audit_status    VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT '审核状态：PENDING-待审核, APPROVED-审核通过, REJECTED-审核驳回',
    audit_admin_id  BIGINT          DEFAULT NULL             COMMENT '审核管理员ID',
    audit_comment   VARCHAR(512)    DEFAULT NULL             COMMENT '审核意见',
    audit_time      DATETIME        DEFAULT NULL             COMMENT '审核时间',
    status          TINYINT(1)      NOT NULL DEFAULT 1       COMMENT '状态：0-停用, 1-进行中, 2-已结课',
    is_deleted      TINYINT(1)      NOT NULL DEFAULT 0       COMMENT '逻辑删除',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_course_id (course_id),
    KEY idx_semester_id (semester_id),
    KEY idx_class_id (class_id),
    KEY idx_teacher_id (teacher_id),
    KEY idx_audit_status (audit_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='开课计划表';

-- 2.4 学生选课/课程报名表
DROP TABLE IF EXISTS student_course_enrollment;
CREATE TABLE student_course_enrollment (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '选课记录ID',
    student_id      BIGINT          NOT NULL                 COMMENT '学生用户ID',
    course_plan_id  BIGINT          NOT NULL                 COMMENT '开课计划ID',
    progress_percent DECIMAL(5,2)   NOT NULL DEFAULT 0.00    COMMENT '学习进度百分比',
    is_completed    TINYINT(1)      NOT NULL DEFAULT 0       COMMENT '是否已完成学习：0-未完成, 1-已完成',
    enroll_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '报名/选课时间',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_student_plan (student_id, course_plan_id),
    KEY idx_course_plan_id (course_plan_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学生选课记录表';

-- 2.5 学生学习进度记录表（记录视频观看进度）
DROP TABLE IF EXISTS student_learning_progress;
CREATE TABLE student_learning_progress (
    id                  BIGINT      NOT NULL AUTO_INCREMENT  COMMENT '记录ID',
    student_id          BIGINT      NOT NULL                 COMMENT '学生用户ID',
    course_id           BIGINT      NOT NULL                 COMMENT '课程ID',
    chapter_id          BIGINT      NOT NULL                 COMMENT '章节ID',
    watched_duration    INT         NOT NULL DEFAULT 0        COMMENT '已观看时长（秒）',
    last_position       INT         NOT NULL DEFAULT 0        COMMENT '上次播放位置（秒）',
    is_completed        TINYINT(1)  NOT NULL DEFAULT 0       COMMENT '是否已完成本章节学习',
    first_watch_time    DATETIME    DEFAULT NULL             COMMENT '首次观看时间',
    last_watch_time     DATETIME    DEFAULT NULL             COMMENT '最后观看时间',
    create_time         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_student_chapter (student_id, chapter_id),
    KEY idx_student_course (student_id, course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学生学习进度记录表';


-- ============================================================
-- 3. 实验实训模块
-- ============================================================

-- 3.1 实验/实训项目表
DROP TABLE IF EXISTS experiment_project;
CREATE TABLE experiment_project (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '项目ID',
    project_name    VARCHAR(256)    NOT NULL                 COMMENT '项目名称',
    description     TEXT            DEFAULT NULL             COMMENT '项目描述/任务要求',
    project_type    VARCHAR(16)     NOT NULL DEFAULT 'EXPERIMENT' COMMENT '项目类型：EXPERIMENT-实验, TRAINING-实训',
    teacher_id      BIGINT          NOT NULL                 COMMENT '创建教师ID',
    guide_file_url  VARCHAR(512)    DEFAULT NULL             COMMENT '指导文档URL',
    -- 审核相关
    audit_status    VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT '审核状态：PENDING-待审核, APPROVED-审核通过, REJECTED-审核驳回',
    audit_admin_id  BIGINT          DEFAULT NULL             COMMENT '审核管理员ID',
    audit_comment   VARCHAR(512)    DEFAULT NULL             COMMENT '审核意见',
    audit_time      DATETIME        DEFAULT NULL             COMMENT '审核时间',
    is_deleted      TINYINT(1)      NOT NULL DEFAULT 0       COMMENT '逻辑删除',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_teacher_id (teacher_id),
    KEY idx_project_type (project_type),
    KEY idx_audit_status (audit_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实验/实训项目表';

-- 3.2 实验/实训任务布置表（将项目分配到班级）
DROP TABLE IF EXISTS experiment_task;
CREATE TABLE experiment_task (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '任务ID',
    project_id      BIGINT          NOT NULL                 COMMENT '关联项目ID',
    class_id        BIGINT          NOT NULL                 COMMENT '任务目标班级ID',
    teacher_id      BIGINT          NOT NULL                 COMMENT '布置教师ID',
    start_time      DATETIME        NOT NULL                 COMMENT '任务开始时间',
    end_time        DATETIME        NOT NULL                 COMMENT '任务截止时间',
    -- 审核相关
    audit_status    VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT '审核状态：PENDING-待审核, APPROVED-审核通过, REJECTED-审核驳回',
    audit_admin_id  BIGINT          DEFAULT NULL             COMMENT '审核管理员ID',
    audit_comment   VARCHAR(512)    DEFAULT NULL             COMMENT '审核意见',
    audit_time      DATETIME        DEFAULT NULL             COMMENT '审核时间',
    status          VARCHAR(16)     NOT NULL DEFAULT 'NOT_STARTED' COMMENT '任务状态：NOT_STARTED-未开始, IN_PROGRESS-进行中, CLOSED-已截止',
    is_deleted      TINYINT(1)      NOT NULL DEFAULT 0       COMMENT '逻辑删除',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_project_id (project_id),
    KEY idx_class_id (class_id),
    KEY idx_end_time (end_time),
    KEY idx_audit_status (audit_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实验/实训任务布置表';

-- 3.3 学生实验/实训提交记录表
DROP TABLE IF EXISTS student_experiment_submission;
CREATE TABLE student_experiment_submission (
    id                  BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '提交记录ID',
    task_id             BIGINT          NOT NULL                 COMMENT '任务ID',
    student_id          BIGINT          NOT NULL                 COMMENT '学生用户ID',
    process_description TEXT            DEFAULT NULL             COMMENT '实训过程描述',
    report_file_url     VARCHAR(512)    DEFAULT NULL             COMMENT '报告文件URL（Word/PDF）',
    report_file_name    VARCHAR(256)    DEFAULT NULL             COMMENT '报告原始文件名',
    -- 评分批阅
    score               DECIMAL(5,1)    DEFAULT NULL             COMMENT '评分（0-100）',
    teacher_comment     VARCHAR(512)    DEFAULT NULL             COMMENT '教师批改评语',
    -- 状态流转
    status              VARCHAR(16)     NOT NULL DEFAULT 'SUBMITTED' COMMENT '状态：SUBMITTED-已提交, GRADED-已批改, RETURNED-已退回（需重交）',
    submit_time         DATETIME        DEFAULT NULL             COMMENT '提交时间',
    grade_time          DATETIME        DEFAULT NULL             COMMENT '批改时间',
    resubmit_count      INT             NOT NULL DEFAULT 0       COMMENT '重新提交次数',
    is_deleted          TINYINT(1)      NOT NULL DEFAULT 0       COMMENT '逻辑删除',
    create_time         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_student (task_id, student_id),
    KEY idx_student_id (student_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学生实验/实训提交记录表';


-- ============================================================
-- 4. 学生成绩模块
-- ============================================================

-- 4.1 学生成绩表
DROP TABLE IF EXISTS student_grade;
CREATE TABLE student_grade (
    id                  BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '成绩ID',
    student_id          BIGINT          NOT NULL                 COMMENT '学生用户ID',
    course_plan_id      BIGINT          NOT NULL                 COMMENT '开课计划ID',
    semester_id         BIGINT          NOT NULL                 COMMENT '学期ID',
    -- 各项成绩
    usual_grade         DECIMAL(5,1)    DEFAULT NULL             COMMENT '平时成绩（0-100）',
    exam_grade          DECIMAL(5,1)    DEFAULT NULL             COMMENT '测验/考试成绩（0-100）',
    experiment_grade    DECIMAL(5,1)    DEFAULT NULL             COMMENT '实验成绩（0-100）',
    training_grade      DECIMAL(5,1)    DEFAULT NULL             COMMENT '实训成绩（0-100）',
    final_grade         DECIMAL(5,1)    DEFAULT NULL             COMMENT '期末总评成绩（0-100）',
    grade_comment       VARCHAR(256)    DEFAULT NULL             COMMENT '成绩评语',
    is_published        TINYINT(1)      NOT NULL DEFAULT 0       COMMENT '是否已发布：0-未发布, 1-已发布',
    create_time         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_student_course_semester (student_id, course_plan_id, semester_id),
    KEY idx_course_plan_id (course_plan_id),
    KEY idx_semester_id (semester_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学生成绩表';


-- ============================================================
-- 5. 教学资源库模块
-- ============================================================

-- 5.1 资源分类表
DROP TABLE IF EXISTS resource_category;
CREATE TABLE resource_category (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '分类ID',
    category_name   VARCHAR(64)     NOT NULL                 COMMENT '分类名称（课件、习题、视频、文档等）',
    parent_id       BIGINT          DEFAULT 0                COMMENT '父分类ID，0为顶级分类',
    sort_order      INT             NOT NULL DEFAULT 0       COMMENT '排序号',
    is_deleted      TINYINT(1)      NOT NULL DEFAULT 0       COMMENT '逻辑删除',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资源分类表';

-- 5.2 教学资源表
DROP TABLE IF EXISTS teaching_resource;
CREATE TABLE teaching_resource (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '资源ID',
    resource_name   VARCHAR(256)    NOT NULL                 COMMENT '资源名称',
    description     VARCHAR(512)    DEFAULT NULL             COMMENT '资源描述',
    category_id     BIGINT          NOT NULL                 COMMENT '资源分类ID',
    file_url        VARCHAR(512)    NOT NULL                 COMMENT '文件存储URL',
    file_name       VARCHAR(256)    NOT NULL                 COMMENT '原始文件名',
    file_type       VARCHAR(32)     NOT NULL                 COMMENT '文件类型：PPT, PDF, DOC, XLS, MP4, ZIP等',
    file_size       BIGINT          NOT NULL DEFAULT 0       COMMENT '文件大小（字节）',
    teacher_id      BIGINT          NOT NULL                 COMMENT '上传教师ID',
    visibility      VARCHAR(16)     NOT NULL DEFAULT 'CLASS' COMMENT '可见范围：CLASS-本班, DEPT-本院, SCHOOL-全校',
    course_id       BIGINT          DEFAULT NULL             COMMENT '关联课程ID（可选）',
    -- 审核相关
    audit_status    VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT '审核状态：PENDING-待审核, APPROVED-审核通过, REJECTED-审核驳回',
    audit_admin_id  BIGINT          DEFAULT NULL             COMMENT '审核管理员ID',
    audit_comment   VARCHAR(512)    DEFAULT NULL             COMMENT '审核意见',
    audit_time      DATETIME        DEFAULT NULL             COMMENT '审核时间',
    -- 统计
    view_count      INT             NOT NULL DEFAULT 0       COMMENT '浏览次数',
    download_count  INT             NOT NULL DEFAULT 0       COMMENT '下载次数',
    is_deleted      TINYINT(1)      NOT NULL DEFAULT 0       COMMENT '逻辑删除',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_category_id (category_id),
    KEY idx_teacher_id (teacher_id),
    KEY idx_visibility (visibility),
    KEY idx_audit_status (audit_status),
    KEY idx_course_id (course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='教学资源表';

-- 5.3 学生资源收藏表
DROP TABLE IF EXISTS student_favorite_resource;
CREATE TABLE student_favorite_resource (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '收藏记录ID',
    student_id      BIGINT          NOT NULL                 COMMENT '学生用户ID',
    resource_id     BIGINT          NOT NULL                 COMMENT '教学资源ID',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_student_resource (student_id, resource_id),
    KEY idx_resource_id (resource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学生资源收藏表';


-- ============================================================
-- 6. 学情数据分析模块
-- ============================================================

-- 6.1 学生每日学习数据统计表
DROP TABLE IF EXISTS learning_analytics_daily;
CREATE TABLE learning_analytics_daily (
    id                      BIGINT      NOT NULL AUTO_INCREMENT  COMMENT '记录ID',
    student_id              BIGINT      NOT NULL                 COMMENT '学生用户ID',
    stat_date               DATE        NOT NULL                 COMMENT '统计日期',
    course_id               BIGINT      NOT NULL                 COMMENT '课程ID',
    study_duration_minutes  INT         NOT NULL DEFAULT 0       COMMENT '当日学习时长（分钟）',
    exercises_completed     INT         NOT NULL DEFAULT 0       COMMENT '当日完成习题数',
    exercises_correct       INT         NOT NULL DEFAULT 0       COMMENT '当日正确习题数',
    tasks_completed         INT         NOT NULL DEFAULT 0       COMMENT '当日完成任务数',
    create_time             DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_student_date_course (student_id, stat_date, course_id),
    KEY idx_stat_date (stat_date),
    KEY idx_course_id (course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学生每日学习数据统计表';

-- 6.2 学业诊断规则配置表
DROP TABLE IF EXISTS academic_diagnosis_rule;
CREATE TABLE academic_diagnosis_rule (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '规则ID',
    rule_name       VARCHAR(128)    NOT NULL                 COMMENT '规则名称',
    diagnosis_level VARCHAR(32)     NOT NULL                 COMMENT '诊断等级：EXCELLENT-优秀, GOOD-良好, TO_IMPROVE-待提升',
    min_score       DECIMAL(5,1)    NOT NULL                 COMMENT '最低分数阈值',
    max_score       DECIMAL(5,1)    NOT NULL                 COMMENT '最高分数阈值',
    description     VARCHAR(512)    DEFAULT NULL             COMMENT '规则说明',
    is_deleted      TINYINT(1)      NOT NULL DEFAULT 0       COMMENT '逻辑删除',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学业诊断规则配置表';

-- 6.3 学业诊断报告表
DROP TABLE IF EXISTS academic_diagnosis;
CREATE TABLE academic_diagnosis (
    id                      BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '诊断报告ID',
    student_id              BIGINT          NOT NULL                 COMMENT '学生用户ID',
    semester_id             BIGINT          NOT NULL                 COMMENT '学期ID',
    total_study_hours       DECIMAL(8,2)    NOT NULL DEFAULT 0.00    COMMENT '学期总学习时长（小时）',
    avg_exercise_accuracy   DECIMAL(5,2)    NOT NULL DEFAULT 0.00    COMMENT '习题平均正确率（%）',
    task_completion_rate    DECIMAL(5,2)    NOT NULL DEFAULT 0.00    COMMENT '任务完成率（%）',
    diagnosis_level         VARCHAR(32)     NOT NULL DEFAULT 'TO_IMPROVE' COMMENT '诊断等级：EXCELLENT-优秀, GOOD-良好, TO_IMPROVE-待提升',
    diagnosis_report        TEXT            DEFAULT NULL             COMMENT '诊断报告详细内容（JSON或富文本）',
    -- 薄弱知识点
    weak_points             VARCHAR(1024)   DEFAULT NULL             COMMENT '薄弱知识点列表（JSON数组）',
    recommend_resources     VARCHAR(1024)   DEFAULT NULL             COMMENT '推荐学习资源ID列表（JSON数组）',
    generated_time          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '报告生成时间',
    create_time             DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_student_id (student_id),
    KEY idx_semester_id (semester_id),
    KEY idx_diagnosis_level (diagnosis_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学业诊断报告表';


-- ============================================================
-- 7. 系统公告与消息模块
-- ============================================================

-- 7.1 系统公告表
DROP TABLE IF EXISTS sys_announcement;
CREATE TABLE sys_announcement (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '公告ID',
    title           VARCHAR(256)    NOT NULL                 COMMENT '公告标题',
    content         LONGTEXT        NOT NULL                 COMMENT '公告内容（富文本）',
    announce_type   VARCHAR(16)     NOT NULL DEFAULT 'SCHOOL' COMMENT '公告类型：SCHOOL-全校, DEPT-院系, CLASS-班级',
    target_dept_id  BIGINT          DEFAULT NULL             COMMENT '目标院系ID（院系公告时填写）',
    target_class_id BIGINT          DEFAULT NULL             COMMENT '目标班级ID（班级公告时填写）',
    publisher_id    BIGINT          NOT NULL                 COMMENT '发布人用户ID',
    attachment_url  VARCHAR(512)    DEFAULT NULL             COMMENT '附件URL',
    is_top          TINYINT(1)      NOT NULL DEFAULT 0       COMMENT '是否置顶：0-否, 1-是',
    status          TINYINT(1)      NOT NULL DEFAULT 1       COMMENT '状态：0-下架, 1-发布',
    publish_time    DATETIME        DEFAULT NULL             COMMENT '发布时间',
    is_deleted      TINYINT(1)      NOT NULL DEFAULT 0       COMMENT '逻辑删除',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_announce_type (announce_type),
    KEY idx_target_dept (target_dept_id),
    KEY idx_target_class (target_class_id),
    KEY idx_publisher (publisher_id),
    KEY idx_publish_time (publish_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统公告表';

-- 7.2 系统消息/通知表
DROP TABLE IF EXISTS sys_message;
CREATE TABLE sys_message (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '消息ID',
    sender_id       BIGINT          DEFAULT NULL             COMMENT '发送人用户ID（系统消息为NULL）',
    receiver_id     BIGINT          NOT NULL                 COMMENT '接收人用户ID',
    title           VARCHAR(256)    NOT NULL                 COMMENT '消息标题',
    content         TEXT            DEFAULT NULL             COMMENT '消息内容',
    message_type    VARCHAR(32)     NOT NULL DEFAULT 'NOTIFICATION' COMMENT '消息类型：NOTIFICATION-通知, REMINDER-提醒, ALERT-预警, APPROVAL-审批通知',
    is_read         TINYINT(1)      NOT NULL DEFAULT 0       COMMENT '是否已读：0-未读, 1-已读',
    read_time       DATETIME        DEFAULT NULL             COMMENT '阅读时间',
    -- 关联业务（用于点击跳转）
    related_biz_type VARCHAR(32)    DEFAULT NULL             COMMENT '关联业务类型：COURSE_PLAN, EXPERIMENT, RESOURCE, ANNOUNCEMENT等',
    related_biz_id  BIGINT          DEFAULT NULL             COMMENT '关联业务ID',
    is_deleted      TINYINT(1)      NOT NULL DEFAULT 0       COMMENT '逻辑删除',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_receiver_read (receiver_id, is_read),
    KEY idx_sender_id (sender_id),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统消息/通知表';


-- ============================================================
-- 8. 系统配置与审计模块
-- ============================================================

-- 8.1 系统配置表
DROP TABLE IF EXISTS sys_config;
CREATE TABLE sys_config (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '配置ID',
    config_key      VARCHAR(128)    NOT NULL                 COMMENT '配置键名',
    config_value    TEXT            NOT NULL                 COMMENT '配置值',
    description     VARCHAR(512)    DEFAULT NULL             COMMENT '配置描述',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置表';

-- 8.2 登录日志表
DROP TABLE IF EXISTS sys_login_log;
CREATE TABLE sys_login_log (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '日志ID',
    user_id         BIGINT          NOT NULL                 COMMENT '用户ID',
    login_ip        VARCHAR(64)     DEFAULT NULL             COMMENT '登录IP地址',
    login_time      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
    logout_time     DATETIME        DEFAULT NULL             COMMENT '退出时间',
    session_id      VARCHAR(128)    DEFAULT NULL             COMMENT '会话ID',
    login_result    VARCHAR(16)     NOT NULL DEFAULT 'SUCCESS' COMMENT '登录结果：SUCCESS-成功, FAIL-失败',
    fail_reason     VARCHAR(256)    DEFAULT NULL             COMMENT '登录失败原因',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id),
    KEY idx_login_time (login_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='登录日志表';

-- 8.3 操作审计日志表（审核流程追溯）
DROP TABLE IF EXISTS sys_audit_log;
CREATE TABLE sys_audit_log (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '审计日志ID',
    biz_type        VARCHAR(32)     NOT NULL                 COMMENT '业务类型：COURSE_PLAN-开课计划, EXPERIMENT-实验实训, RESOURCE-教学资源',
    biz_id          BIGINT          NOT NULL                 COMMENT '业务数据ID',
    biz_name        VARCHAR(256)    DEFAULT NULL             COMMENT '业务数据名称（冗余，便于查询）',
    action          VARCHAR(32)     NOT NULL                 COMMENT '操作动作：SUBMIT-提交审核, APPROVE-通过, REJECT-驳回, WITHDRAW-撤回',
    operator_id     BIGINT          NOT NULL                 COMMENT '操作人用户ID',
    operator_name   VARCHAR(64)     NOT NULL                 COMMENT '操作人姓名（冗余）',
    comment         VARCHAR(1024)   DEFAULT NULL             COMMENT '操作备注/审核意见',
    before_status   VARCHAR(32)     DEFAULT NULL             COMMENT '操作前状态',
    after_status    VARCHAR(32)     NOT NULL                 COMMENT '操作后状态',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_biz (biz_type, biz_id),
    KEY idx_operator (operator_id),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作审计日志表';


-- ============================================================
-- 9. 初始化默认数据
-- ============================================================

-- 9.1 初始化角色数据
INSERT INTO sys_role (role_code, role_name, description) VALUES
('ADMIN',    '管理员', '系统管理员，负责账号管理、内容审核、系统配置、全局监控'),
('TEACHER',  '教师',   '教师用户，负责课程管理、实验实训、资源上传、学情统计'),
('STUDENT',  '学生',   '学生用户，负责课程学习、作业提交、资源浏览、学情查看');

-- 9.2 初始化默认管理员账号（密码：admin123）
INSERT INTO sys_user (username, password, real_name, role_id, status) VALUES
('admin', 'admin123', '系统管理员', 1, 1);

-- 9.3 初始化系统配置默认值
INSERT INTO sys_config (config_key, config_value, description) VALUES
('current_semester',     '',                       '当前学期，格式：2024-2025-1'),
('current_school_year',  '',                       '当前学年，格式：2024-2025'),
('max_file_upload_size', '104857600',              '文件上传最大大小（字节），默认100MB'),
('allowed_file_types',   'ppt,pdf,doc,docx,xls,xlsx,mp4,zip', '允许上传的文件类型'),
('password_min_length',  '6',                      '密码最小长度'),
('password_complexity',  'MEDIUM',                 '密码复杂度要求：LOW-低, MEDIUM-中, HIGH-高'),
('session_timeout_minutes', '30',                  '会话超时时间（分钟）'),
('max_login_fail_count', '5',                      '连续登录失败锁定次数'),
('default_password',     '123456',                 '新建账号默认密码'),
('excellent_threshold',  '85',                     '学业优秀分数线'),
('good_threshold',       '70',                     '学业良好分数线（低于此值为待提升）');

-- 9.4 初始化学业诊断规则
INSERT INTO academic_diagnosis_rule (rule_name, diagnosis_level, min_score, max_score, description) VALUES
('优秀标准', 'EXCELLENT',  85, 100, '综合评分≥85分为优秀'),
('良好标准', 'GOOD',       70, 84.9, '综合评分70-84.9分为良好'),
('待提升标准', 'TO_IMPROVE', 0, 69.9, '综合评分<70分需提升');

-- 9.5 初始化资源分类
INSERT INTO resource_category (category_name, parent_id, sort_order) VALUES
('课件', 0, 1),
('习题', 0, 2),
('视频', 0, 3),
('文档', 0, 4),
('其他', 0, 5);

-- 9.6 初始化院系数据（示例）
INSERT INTO sys_department (dept_name, dept_code, sort_order) VALUES
('计算机科学与技术学院', 'CS', 1),
('软件工程学院',         'SE', 2),
('信息与通信工程学院',   'ICE', 3);

-- ============================================================
-- 完成
-- ============================================================
