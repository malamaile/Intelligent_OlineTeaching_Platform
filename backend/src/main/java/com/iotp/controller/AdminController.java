package com.iotp.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.iotp.common.Result;
import com.iotp.service.AdminService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.Map;

/**
 * 管理员端控制器
 * <p>提供用户管理、审核管理（课程/任务/资源）、系统设置、公告管理、
 * 全局学情分析等 RESTful API 接口。</p>
 */
@RestController
@RequestMapping("/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    @Autowired
    private AdminService adminService;

    // ==================== 用户管理 ====================

    /**
     * 分页查询用户列表
     *
     * GET /admin/users?role=STUDENT&keyword=张三&department=1&className=计科一班&status=1&page=1&pageSize=10
     */
    @GetMapping("/users")
    public Result<IPage<Map<String, Object>>> getUsers(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long department,
            @RequestParam(required = false) String className,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        IPage<Map<String, Object>> data = adminService.getUsers(role, keyword, department,
                className, status, page, pageSize);
        return Result.ok(data);
    }

    /**
     * 获取用户详细信息
     *
     * GET /admin/users/{userId}
     */
    @GetMapping("/users/{userId}")
    public Result<Map<String, Object>> getUserDetail(@PathVariable Long userId) {
        Map<String, Object> data = adminService.getUserDetail(userId);
        return Result.ok(data);
    }

    /**
     * 创建用户
     *
     * POST /admin/users
     * Body: { "account": "zhangsan", "userName": "张三", "role": "STUDENT",
     *         "password": "123456", "department": 1, "email": "zs@test.com",
     *         "phone": "13800138000", "className": "计科一班" }
     */
    @PostMapping("/users")
    public Result<Map<String, Object>> createUser(@RequestBody Map<String, Object> body) {
        String account = (String) body.get("account");
        String userName = (String) body.get("userName");
        String role = (String) body.get("role");
        String password = (String) body.get("password");
        String email = (String) body.get("email");
        String phone = (String) body.get("phone");
        String className = (String) body.get("className");

        // department 支持 ID 或名称
        Long department = parseDepartmentId(body.get("department"));

        Map<String, Object> data = adminService.createUser(account, userName, role,
                password, department, email, phone, className);
        return Result.ok("用户创建成功", data);
    }

    /**
     * 解析 department 参数：支持 Long ID 或 String 名称
     */
    private Long parseDepartmentId(Object deptVal) {
        if (deptVal == null) return null;
        String deptStr = deptVal.toString().trim();
        if (deptStr.isEmpty()) return null;
        try {
            return Long.valueOf(deptStr);
        } catch (NumberFormatException e) {
            // 按名称查找
            return adminService.getDepartmentIdByName(deptStr);
        }
    }

    /**
     * 批量导入用户（Excel）
     *
     * POST /admin/users/import
     * RequestParam: file (MultipartFile)
     */
    @PostMapping("/users/import")
    public Result<Map<String, Object>> importUsers(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return Result.error(400, "上传文件不能为空");
        }
        Map<String, Object> data = adminService.importUsers(file);
        return Result.ok("导入完成", data);
    }

    /**
     * 更新用户信息
     *
     * PUT /admin/users/{userId}
     * Body: { "userName": "李四", "email": "ls@test.com", "phone": "13900139000" }
     */
    @PutMapping("/users/{userId}")
    public Result<String> updateUser(
            @PathVariable Long userId,
            @RequestBody Map<String, Object> body) {
        adminService.updateUser(userId, body);
        return Result.ok("用户信息更新成功");
    }

    /**
     * 更新用户状态（启用/冻结/锁定）
     *
     * PUT /admin/users/{userId}/status
     * Body: { "status": "FROZEN", "reason": "违反平台规定" }
     */
    @PutMapping("/users/{userId}/status")
    public Result<String> updateUserStatus(
            @PathVariable Long userId,
            @RequestBody Map<String, Object> body) {
        String status = (String) body.get("status");
        String reason = (String) body.get("reason");

        if (status == null || status.isEmpty()) {
            return Result.error(400, "状态不能为空");
        }

        adminService.updateUserStatus(userId, status, reason);
        return Result.ok("用户状态更新成功");
    }

    /**
     * 重置用户密码
     *
     * PUT /admin/users/{userId}/reset-password
     * Body: { "newPassword": "newPass123" }（newPassword 可选，为空则使用系统默认密码）
     */
    @PutMapping("/users/{userId}/reset-password")
    public Result<String> resetUserPassword(
            @PathVariable Long userId,
            @RequestBody Map<String, Object> body) {
        String newPassword = (String) body.get("newPassword");
        adminService.resetUserPassword(userId, newPassword);
        return Result.ok("密码重置成功");
    }

    // ==================== 课程审核 ====================

    /**
     * 分页查询待审核课程（开课计划）
     *
     * GET /admin/audit/courses?status=PENDING&semester=1&department=1&page=1&pageSize=10
     */
    @GetMapping("/audit/courses")
    public Result<IPage<Map<String, Object>>> getAuditCourses(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long semester,
            @RequestParam(required = false) Long department,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        IPage<Map<String, Object>> data = adminService.getAuditCourses(status, semester,
                department, page, pageSize);
        return Result.ok(data);
    }

    /**
     * 审核课程
     *
     * POST /admin/audit/courses/{courseId}
     * Body: { "action": "APPROVED", "comment": "审核通过" }
     */
    @PostMapping("/audit/courses/{courseId}")
    public Result<String> auditCourse(
            @PathVariable Long courseId,
            @RequestBody Map<String, Object> body) {
        String action = (String) body.get("action");
        String comment = (String) body.get("comment");

        if (action == null || action.isEmpty()) {
            return Result.error(400, "审核动作不能为空");
        }

        adminService.auditCourse(courseId, action, comment);
        return Result.ok("课程审核完成");
    }

    /**
     * 查询课程审核日志
     *
     * GET /admin/audit/courses/logs?courseId=1&auditorId=1&startDate=2026-01-01&endDate=2026-12-31&page=1&pageSize=10
     */
    @GetMapping("/audit/courses/logs")
    public Result<IPage<Map<String, Object>>> getAuditCourseLogs(
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) Long auditorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        IPage<Map<String, Object>> data = adminService.getAuditCourseLogs(courseId, auditorId,
                startDate, endDate, page, pageSize);
        return Result.ok(data);
    }

    /**
     * 获取课程审核统计
     *
     * GET /admin/audit/courses/statistics
     */
    @GetMapping("/audit/courses/statistics")
    public Result<Map<String, Object>> getCourseStatistics() {
        Map<String, Object> data = adminService.getCourseStatistics();
        return Result.ok(data);
    }

    // ==================== 任务审核 ====================

    /**
     * 分页查询待审核实验任务
     *
     * GET /admin/audit/tasks?status=PENDING&taskType=EXPERIMENT&page=1&pageSize=10
     */
    @GetMapping("/audit/tasks")
    public Result<IPage<Map<String, Object>>> getAuditTasks(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String taskType,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        IPage<Map<String, Object>> data = adminService.getAuditTasks(status, taskType, page, pageSize);
        return Result.ok(data);
    }

    /**
     * 审核实验任务
     *
     * POST /admin/audit/tasks/{taskId}
     * Body: { "action": "APPROVED", "comment": "审核通过" }
     */
    @PostMapping("/audit/tasks/{taskId}")
    public Result<String> auditTask(
            @PathVariable Long taskId,
            @RequestBody Map<String, Object> body) {
        String action = (String) body.get("action");
        String comment = (String) body.get("comment");

        if (action == null || action.isEmpty()) {
            return Result.error(400, "审核动作不能为空");
        }

        adminService.auditTask(taskId, action, comment);
        return Result.ok("任务审核完成");
    }

    /**
     * 获取任务审核统计
     *
     * GET /admin/audit/tasks/statistics
     */
    @GetMapping("/audit/tasks/statistics")
    public Result<Map<String, Object>> getTaskStatistics() {
        Map<String, Object> data = adminService.getTaskStatistics();
        return Result.ok(data);
    }

    // ==================== 资源审核 ====================

    /**
     * 分页查询待审核教学资源
     *
     * GET /admin/audit/resources?status=PENDING&type=pdf&page=1&pageSize=10
     */
    @GetMapping("/audit/resources")
    public Result<IPage<Map<String, Object>>> getAuditResources(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        IPage<Map<String, Object>> data = adminService.getAuditResources(status, type, page, pageSize);
        return Result.ok(data);
    }

    /**
     * 审核教学资源
     *
     * POST /admin/audit/resources/{resourceId}
     * Body: { "action": "APPROVED", "comment": "审核通过" }
     */
    @PostMapping("/audit/resources/{resourceId}")
    public Result<String> auditResource(
            @PathVariable Long resourceId,
            @RequestBody Map<String, Object> body) {
        String action = (String) body.get("action");
        String comment = (String) body.get("comment");

        if (action == null || action.isEmpty()) {
            return Result.error(400, "审核动作不能为空");
        }

        adminService.auditResource(resourceId, action, comment);
        return Result.ok("资源审核完成");
    }

    /**
     * 获取资源审核统计
     *
     * GET /admin/audit/resources/statistics
     */
    @GetMapping("/audit/resources/statistics")
    public Result<Map<String, Object>> getResourceStatistics() {
        Map<String, Object> data = adminService.getResourceStatistics();
        return Result.ok(data);
    }

    // ==================== 系统设置 ====================

    /**
     * 获取系统设置
     *
     * GET /admin/settings
     */
    @GetMapping("/settings")
    public Result<Map<String, Object>> getSettings() {
        Map<String, Object> data = adminService.getSettings();
        return Result.ok(data);
    }

    /**
     * 更新系统设置
     *
     * PUT /admin/settings
     * Body: { "default_password": "abc123", "max_file_upload_size": "209715200", ... }
     */
    @PutMapping("/settings")
    public Result<String> updateSettings(@RequestBody Map<String, String> body) {
        adminService.updateSettings(body);
        return Result.ok("系统设置更新成功");
    }

    // ==================== 公告管理 ====================

    /**
     * 创建系统公告
     *
     * POST /admin/announcements
     * Body: { "title": "系统维护通知", "content": "系统将于...", "scope": "ALL",
     *         "department": 1, "importance": "URGENT" }
     */
    @PostMapping("/announcements")
    public Result<Map<String, Object>> createAnnouncement(@RequestBody Map<String, Object> body) {
        String title = (String) body.get("title");
        String content = (String) body.get("content");
        String scope = (String) body.get("scope");
        Long department = body.get("department") != null
                ? Long.valueOf(body.get("department").toString()) : null;
        String importance = (String) body.get("importance");

        if (title == null || title.isEmpty()) {
            return Result.error(400, "公告标题不能为空");
        }
        if (content == null || content.isEmpty()) {
            return Result.error(400, "公告内容不能为空");
        }

        Map<String, Object> data = adminService.createAnnouncement(title, content, scope,
                department, importance);
        return Result.ok("公告发布成功", data);
    }

    /**
     * 分页查询公告列表
     *
     * GET /admin/announcements?scope=ALL&keyword=维护&startDate=2026-01-01&endDate=2026-12-31&page=1&pageSize=10
     */
    @GetMapping("/announcements")
    public Result<IPage<Map<String, Object>>> getAnnouncements(
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        IPage<Map<String, Object>> data = adminService.getAnnouncements(scope, keyword,
                startDate, endDate, page, pageSize);
        return Result.ok(data);
    }

    /**
     * 更新公告
     *
     * PUT /admin/announcements/{announcementId}
     * Body: { "title": "新标题", "content": "新内容", "importance": "IMPORTANT" }
     */
    @PutMapping("/announcements/{announcementId}")
    public Result<String> updateAnnouncement(
            @PathVariable Long announcementId,
            @RequestBody Map<String, Object> body) {
        adminService.updateAnnouncement(announcementId, body);
        return Result.ok("公告更新成功");
    }

    /**
     * 删除公告
     *
     * DELETE /admin/announcements/{announcementId}
     */
    @DeleteMapping("/announcements/{announcementId}")
    public Result<String> deleteAnnouncement(@PathVariable Long announcementId) {
        adminService.deleteAnnouncement(announcementId);
        return Result.ok("公告删除成功");
    }

    // ==================== 全局学情分析 ====================

    /**
     * 获取全校学情总览
     *
     * GET /admin/analytics/overview?semester=1
     */
    @GetMapping("/analytics/overview")
    public Result<Map<String, Object>> getOverview(
            @RequestParam(required = false) Long semester) {
        Map<String, Object> data = adminService.getOverview(semester);
        return Result.ok(data);
    }

    /**
     * 分页查询预警学生列表
     *
     * GET /admin/analytics/warnings?department=1&riskLevel=HIGH&warningType=SCORE&page=1&pageSize=10
     */
    @GetMapping("/analytics/warnings")
    public Result<Map<String, Object>> getWarnings(
            @RequestParam(required = false) Long department,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) String warningType,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Map<String, Object> data = adminService.getWarnings(department, riskLevel,
                warningType, page, pageSize);
        return Result.ok(data);
    }

    /**
     * 导出分析报告（功能开发中）
     *
     * GET /admin/analytics/export?semester=1&department=1&format=PDF
     */
    @GetMapping("/analytics/export")
    public Result<Map<String, Object>> exportReport(
            @RequestParam(required = false) Long semester,
            @RequestParam(required = false) Long department,
            @RequestParam(defaultValue = "PDF") String format) {
        Map<String, Object> data = adminService.exportReport(semester, department, format);
        return Result.ok("导出功能开发中", data);
    }
}
