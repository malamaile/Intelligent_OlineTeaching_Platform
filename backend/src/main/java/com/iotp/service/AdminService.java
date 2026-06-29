package com.iotp.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 管理员端业务服务接口
 * <p>提供用户管理、审核管理（课程/任务/资源）、系统设置、公告管理、
 * 全校学情分析等核心管理功能。</p>
 */
public interface AdminService {

    // ==================== 用户管理 ====================

    /**
     * 分页查询用户列表，支持多条件过滤
     *
     * @param role       角色编码（STUDENT/TEACHER/ADMIN）
     * @param keyword    关键字（用户名/姓名/手机号模糊匹配）
     * @param department 部门ID
     * @param className  班级名称
     * @param status     状态（1启用/0禁用）
     * @param page       页码
     * @param pageSize   每页条数
     * @return 分页结果，包含角色名称、部门名称、班级名称
     */
    IPage<Map<String, Object>> getUsers(String role, String keyword, Long department,
                                         String className, Integer status,
                                         Integer page, Integer pageSize);

    /**
     * 获取用户详细信息（含角色、部门、班级名称）
     *
     * @param userId 用户ID
     * @return 用户详情Map
     */
    Map<String, Object> getUserDetail(Long userId);

    /**
     * 创建用户
     *
     * @param account    登录账号
     * @param userName   真实姓名
     * @param role       角色编码
     * @param password   密码（为空则使用默认密码）
     * @param department 部门ID
     * @param email      邮箱
     * @param phone      手机号
     * @param className  班级名称
     * @return 创建后的用户信息
     */
    Map<String, Object> createUser(String account, String userName, String role,
                                    String password, Long department,
                                    String email, String phone, String className);

    /**
     * 批量导入用户（解析 Excel 文件）
     *
     * @param file 上传的 Excel 文件（.xlsx）
     * @return 导入结果：{successCount, failCount, failDetails[]}
     */
    Map<String, Object> importUsers(MultipartFile file);

    /**
     * 更新用户信息
     *
     * @param userId  用户ID
     * @param updates 待更新字段 Map
     */
    void updateUser(Long userId, Map<String, Object> updates);

    /**
     * 更新用户状态（启用/冻结/锁定）
     *
     * @param userId 用户ID
     * @param status 状态编码
     * @param reason 变更原因
     */
    /**
     * 根据部门名称查找部门ID
     *
     * @param deptName 部门名称
     * @return 部门ID，不存在时返回 null
     */
    Long getDepartmentIdByName(String deptName);

    void updateUserStatus(Long userId, String status, String reason);

    /**
     * 重置用户密码
     *
     * @param userId      用户ID
     * @param newPassword 新密码（为空则使用系统默认密码）
     */
    void resetUserPassword(Long userId, String newPassword);

    // ==================== 课程审核 ====================

    /**
     * 分页查询待审核课程（开课计划）
     *
     * @param status     审核状态（PENDING/APPROVED/REJECTED）
     * @param semester   学期ID
     * @param department 部门ID
     * @param page       页码
     * @param pageSize   每页条数
     * @return 分页结果，含课程、教师、班级、部门信息
     */
    IPage<Map<String, Object>> getAuditCourses(String status, Long semester,
                                                Long department,
                                                Integer page, Integer pageSize);

    /**
     * 审核课程（开课计划）
     *
     * @param courseId 开课计划ID（CoursePlan.id）
     * @param action   审核动作（APPROVED/REJECTED）
     * @param comment  审核意见
     */
    void auditCourse(Long courseId, String action, String comment);

    /**
     * 查询课程审核日志
     *
     * @param courseId   课程/开课计划ID（可选）
     * @param auditorId  审核人ID（可选）
     * @param startDate  开始日期（可选）
     * @param endDate    结束日期（可选）
     * @param page       页码
     * @param pageSize   每页条数
     * @return 分页审核日志
     */
    IPage<Map<String, Object>> getAuditCourseLogs(Long courseId, Long auditorId,
                                                   LocalDate startDate, LocalDate endDate,
                                                   Integer page, Integer pageSize);

    /**
     * 获取课程审核统计（按部门和学期汇总）
     *
     * @return 统计结果
     */
    Map<String, Object> getCourseStatistics();

    // ==================== 任务审核 ====================

    /**
     * 分页查询待审核实验任务
     *
     * @param status   审核状态
     * @param taskType 任务类型（EXPERIMENT/TRAINING）
     * @param page     页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    IPage<Map<String, Object>> getAuditTasks(String status, String taskType,
                                              Integer page, Integer pageSize);

    /**
     * 审核实验任务
     *
     * @param taskId  任务ID
     * @param action  审核动作
     * @param comment 审核意见
     */
    void auditTask(Long taskId, String action, String comment);

    /**
     * 获取任务审核统计
     *
     * @return 统计结果（通过率、部门分布等）
     */
    Map<String, Object> getTaskStatistics();

    // ==================== 资源审核 ====================

    /**
     * 分页查询待审核教学资源
     *
     * @param status   审核状态
     * @param type     资源类型（文件扩展名）
     * @param page     页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    IPage<Map<String, Object>> getAuditResources(String status, String type,
                                                  Integer page, Integer pageSize);

    /**
     * 审核教学资源
     *
     * @param resourceId 资源ID
     * @param action     审核动作
     * @param comment    审核意见
     */
    void auditResource(Long resourceId, String action, String comment);

    /**
     * 获取资源审核统计
     *
     * @return 统计结果（总数、下载量、浏览量等）
     */
    Map<String, Object> getResourceStatistics();

    // ==================== 系统设置 ====================

    /**
     * 获取系统设置（将所有 SysConfig 按分组读取为结构化 Map）
     *
     * @return 设置信息：currentSemester, currentSchoolYear, maxFileUploadSize,
     *         allowedFileTypes, academicThresholds, passwordRule 等
     */
    Map<String, Object> getSettings();

    /**
     * 更新系统设置
     *
     * @param settingsMap 待更新的配置键值对
     */
    void updateSettings(Map<String, String> settingsMap);

    // ==================== 公告管理 ====================

    /**
     * 创建系统公告
     *
     * @param title      公告标题
     * @param content    公告内容
     * @param scope      发布范围（ALL/DEPARTMENT/CLASS）
     * @param department 目标部门ID（scope=DEPARTMENT时必填）
     * @param importance 重要程度（URGENT/IMPORTANT/NORMAL）
     * @return 创建的公告信息
     */
    Map<String, Object> createAnnouncement(String title, String content, String scope,
                                            Long department, String importance);

    /**
     * 分页查询公告列表
     *
     * @param scope     发布范围
     * @param keyword   关键字
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @param page      页码
     * @param pageSize  每页条数
     * @return 分页结果
     */
    IPage<Map<String, Object>> getAnnouncements(String scope, String keyword,
                                                 LocalDate startDate, LocalDate endDate,
                                                 Integer page, Integer pageSize);

    /**
     * 更新公告
     *
     * @param announcementId 公告ID
     * @param updates        待更新字段
     */
    void updateAnnouncement(Long announcementId, Map<String, Object> updates);

    /**
     * 删除公告
     *
     * @param announcementId 公告ID
     */
    void deleteAnnouncement(Long announcementId);

    // ==================== 全局学情分析 ====================

    /**
     * 获取全校学情总览
     *
     * @param semester 学期ID（可选，为空则使用当前学期）
     * @return 总览数据：totalStudents, totalCourses, overallCompletionRate,
     *         overallPassRate, levelDistribution, byDepartment[], trendData[]
     */
    Map<String, Object> getOverview(Long semester);

    /**
     * 分页查询预警学生列表
     *
     * @param department  部门ID
     * @param riskLevel   风险等级（HIGH/MEDIUM/LOW）
     * @param warningType 预警类型
     * @param page        页码
     * @param pageSize    每页条数
     * @return 分页结果，含汇总信息（highRisk, mediumRisk, lowRisk 计数）
     */
    Map<String, Object> getWarnings(Long department, String riskLevel,
                                     String warningType, Integer page, Integer pageSize);

    /**
     * 导出分析报告（占位实现）
     *
     * @param semester   学期ID
     * @param department 部门ID
     * @param format     导出格式（PDF/EXCEL）
     * @return 导出结果
     */
    Map<String, Object> exportReport(Long semester, Long department, String format);
}
