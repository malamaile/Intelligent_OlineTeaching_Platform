package com.iotp.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.iotp.entity.*;

import java.util.List;
import java.util.Map;

/**
 * 教师端业务服务接口
 * <p>提供教师首页看板、课程管理、实验任务管理、成绩管理、教学资源管理、
 * 学情分析、通知公告、消息管理等教师端功能。</p>
 */
public interface TeacherService {

    // ==================== 首页看板 ====================

    /**
     * 获取教师首页看板数据
     *
     * @return 包含待审核/待批阅数量、预警学生、最近通知、班级概况的 Map
     */
    Map<String, Object> getDashboard();

    // ==================== 课程管理 ====================

    /**
     * 分页查询教师课程列表
     *
     * @param semester    学期 ID（可选）
     * @param auditStatus 审核状态（可选）
     * @param keyword     课程名称关键字（可选）
     * @param page        页码
     * @param pageSize    每页条数
     * @return 带课程信息和开课计划的分页数据
     */
    IPage<Map<String, Object>> getCourses(Long semester, String auditStatus, String keyword,
                                          Integer page, Integer pageSize);

    /**
     * 创建课程（含开课计划和章节）
     *
     * @param courseData 课程数据：包含 courseName, courseCode, description, coverImage, departmentId,
     *                   semesterId, classId, scheduleInfo 以及 chapters（章节数组）
     * @return 创建的课程信息
     */
    Map<String, Object> createCourse(Map<String, Object> courseData);

    /**
     * 更新课程信息
     *
     * @param courseId   课程 ID
     * @param courseData 需更新的课程数据
     */
    void updateCourse(Long courseId, Map<String, Object> courseData);

    /**
     * 删除课程（软删除，仅 PENDING 或 REJECTED 状态可删除）
     *
     * @param courseId 课程 ID
     */
    void deleteCourse(Long courseId);

    /**
     * 获取课程学习进度
     *
     * @param courseId 课程 ID
     * @return 包含课程名称、总体进度、章节进度、学生进度的 Map
     */
    Map<String, Object> getCourseProgress(Long courseId);

    /**
     * 录入/修改成绩
     *
     * @param courseId 课程 ID
     * @param grades   成绩列表，每条包含 studentId, coursePlanId, semesterId,
     *                 usualGrade, examGrade, experimentGrade
     * @return 更新的记录数
     */
    int saveGrades(Long courseId, List<Map<String, Object>> grades);

    /**
     * 查询课程成绩
     *
     * @param courseId 课程 ID
     * @return 成绩列表，包含学生信息和各分项成绩
     */
    List<Map<String, Object>> getGrades(Long courseId);

    // ==================== 实验任务管理 ====================

    /**
     * 分页查询实验任务
     *
     * @param taskType   项目类型（可选）
     * @param courseId   课程 ID（可选）
     * @param auditStatus 审核状态（可选）
     * @param page       页码
     * @param pageSize   每页条数
     * @return 带项目信息的分页数据
     */
    IPage<Map<String, Object>> getTasks(String taskType, Long courseId, String auditStatus,
                                        Integer page, Integer pageSize);

    /**
     * 创建实验任务（含项目）
     *
     * @param taskData 任务数据
     * @return 创建的任务信息
     */
    Map<String, Object> createTask(Map<String, Object> taskData);

    /**
     * 更新实验任务
     *
     * @param taskId   任务 ID
     * @param taskData 需更新的数据
     */
    void updateTask(Long taskId, Map<String, Object> taskData);

    /**
     * 删除实验任务（软删除）
     *
     * @param taskId 任务 ID
     */
    void deleteTask(Long taskId);

    /**
     * 获取实验任务提交列表
     *
     * @param taskId   任务 ID
     * @param status   提交状态（可选）
     * @param page     页码
     * @param pageSize 每页条数
     * @return 带统计信息的分页提交数据
     */
    Map<String, Object> getTaskSubmissions(Long taskId, String status, Integer page, Integer pageSize);

    /**
     * 批阅提交
     *
     * @param submissionId 提交 ID
     * @param score        分数
     * @param comment      评语
     * @param action       操作：PASS 通过 / RETURN 退回
     * @return 操作结果
     */
    Map<String, Object> gradeSubmission(Long submissionId, Integer score, String comment, String action);

    /**
     * 退回提交（含退回原因）
     *
     * @param submissionId 提交 ID
     * @param returnReason 退回原因
     * @return 操作结果
     */
    Map<String, Object> returnSubmission(Long submissionId, String returnReason);

    // ==================== 教学资源管理 ====================

    /**
     * 分页查询教学资源
     *
     * @param auditStatus 审核状态（可选）
     * @param type        资源类型（可选）
     * @param keyword     资源名称关键字（可选）
     * @param page        页码
     * @param pageSize    每页条数
     * @return 带分类信息的分页数据
     */
    IPage<Map<String, Object>> getResources(String auditStatus, String type, String keyword,
                                            Integer page, Integer pageSize);

    /**
     * 上传教学资源
     *
     * @param resourceData 资源数据
     * @return 创建的资源信息
     */
    Map<String, Object> uploadResource(Map<String, Object> resourceData);

    /**
     * 更新教学资源
     *
     * @param resourceId   资源 ID
     * @param resourceData 需更新的数据
     */
    void updateResource(Long resourceId, Map<String, Object> resourceData);

    /**
     * 删除教学资源（软删除）
     *
     * @param resourceId 资源 ID
     */
    void deleteResource(Long resourceId);

    /**
     * 重新提交审核（将已驳回的资源重新提交）
     *
     * @param resourceId 资源 ID
     * @param resourceData 更新后的资源数据
     */
    void resubmitResource(Long resourceId, Map<String, Object> resourceData);

    // ==================== 学情分析 ====================

    /**
     * 获取班级学情总览
     *
     * @param classId  班级 ID（可选，为空则查所有授课班级）
     * @param semester 学期 ID（可选）
     * @return 包含班级人数、平均完成率、平均分、等级分布、课程排名
     */
    Map<String, Object> getClassOverview(Long classId, Long semester);

    /**
     * 获取某个学生的学情分析（教师视角）
     *
     * @param userId   学生 ID
     * @param semester 学期 ID（可选）
     * @return 学生的成绩、进度、诊断信息
     */
    Map<String, Object> getStudentAnalytics(Long userId, Long semester);

    /**
     * 获取预警学生列表（分页）
     *
     * @param classId    班级 ID（可选）
     * @param filterType 预警类型：MISSING_HOMEWORK / LOW_SCORE / SLOW_PROGRESS / ALL
     * @param page       当前页
     * @param pageSize   每页条数
     * @return 预警学生分页数据
     */
    IPage<Map<String, Object>> getAtRiskStudents(Long classId, String filterType, Integer page, Integer pageSize);

    // ==================== 通知公告 ====================

    /**
     * 发布班级通知
     *
     * @param classId    目标班级 ID
     * @param title      通知标题
     * @param content    通知内容
     * @param importance 重要程度（可选）
     * @return 创建的通知
     */
    Map<String, Object> createNotice(Long classId, String title, String content, String importance);

    /**
     * 分页查询教师发布的公告
     *
     * @param classId  班级 ID（可选）
     * @param page     页码
     * @param pageSize 每页条数
     * @return 公告分页数据
     */
    IPage<Map<String, Object>> getNotices(Long classId, Integer page, Integer pageSize);

    /**
     * 删除公告（软删除）
     *
     * @param noticeId 公告 ID
     */
    void deleteNotice(Long noticeId);

    // ==================== 消息 ====================

    /**
     * 分页查询教师消息
     *
     * @param page     页码
     * @param pageSize 每页条数
     * @return 带未读数的消息分页数据
     */
    Map<String, Object> getTeacherMessages(Integer page, Integer pageSize);

    /**
     * 标记单条消息已读
     *
     * @param messageId 消息 ID
     */
    void markMessageAsRead(Long messageId);

    /**
     * 标记所有消息已读
     */
    void markAllMessagesAsRead();

    // ==================== 导出/上传辅助功能 ====================

    /**
     * 导出课程成绩为 Excel 文件
     *
     * @param courseId 课程 ID
     * @return Excel 文件字节数组
     */
    byte[] exportGrades(Long courseId);

    /**
     * 上传实验任务指导文件（更新项目 guideFileUrl）
     *
     * @param taskId   任务 ID
     * @param fileUrl  文件 URL
     * @param fileName 文件名
     */
    void uploadGuideFile(Long taskId, String fileUrl, String fileName);

    /**
     * 获取教学资源审核反馈
     *
     * @param resourceId 资源 ID
     * @return 包含审核日志列表和最新审核意见的 Map
     */
    Map<String, Object> getAuditFeedback(Long resourceId);

    /**
     * 导出学情分析为 Excel 文件
     *
     * @param classId  班级 ID（可选）
     * @param semester 学期 ID（可选）
     * @return Excel 文件字节数组
     */
    byte[] exportAnalytics(Long classId, Long semester);

}
