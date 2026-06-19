package com.iotp.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.iotp.common.Result;
import com.iotp.service.TeacherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 教师端控制器
 * <p>提供教师首页看板、课程管理、实验任务管理、成绩管理、教学资源管理、
 * 学情分析、通知公告、消息通知等 RESTful API 接口。</p>
 *
 * @author 杨雨洁
 * @since 2026-06-18
 */
@RestController
@RequestMapping("/teacher")
public class TeacherController {

    private static final Logger log = LoggerFactory.getLogger(TeacherController.class);

    @Autowired
    private TeacherService teacherService;

    // ==================== 首页看板 ====================

    /**
     * 获取教师首页看板数据
     *
     * GET /teacher/dashboard
     */
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> getDashboard() {
        Map<String, Object> data = teacherService.getDashboard();
        return Result.ok(data);
    }

    // ==================== 课程管理 ====================

    /**
     * 分页查询教师课程列表
     *
     * GET /teacher/courses?semester=1&auditStatus=PENDING&keyword=xxx&page=1&pageSize=10
     */
    @GetMapping("/courses")
    public Result<IPage<Map<String, Object>>> getCourses(
            @RequestParam(required = false) Long semester,
            @RequestParam(required = false) String auditStatus,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        IPage<Map<String, Object>> data = teacherService.getCourses(semester, auditStatus, keyword, page, pageSize);
        return Result.ok(data);
    }

    /**
     * 创建课程（含开课计划和章节）
     *
     * POST /teacher/courses
     * Body: { "courseName": "...", "courseCode": "...", "description": "...",
     *         "semesterId": 1, "classId": 1, "chapters": [...] }
     */
    @PostMapping("/courses")
    public Result<Map<String, Object>> createCourse(@RequestBody Map<String, Object> courseData) {
        Map<String, Object> data = teacherService.createCourse(courseData);
        return Result.ok("课程创建成功，待审核", data);
    }

    /**
     * 更新课程信息
     *
     * PUT /teacher/courses/{courseId}
     * Body: { "courseName": "...", "chapters": [...] }
     */
    @PutMapping("/courses/{courseId}")
    public Result<String> updateCourse(
            @PathVariable Long courseId,
            @RequestBody Map<String, Object> courseData) {
        teacherService.updateCourse(courseId, courseData);
        return Result.ok("课程更新成功");
    }

    /**
     * 删除课程（软删除）
     *
     * DELETE /teacher/courses/{courseId}
     */
    @DeleteMapping("/courses/{courseId}")
    public Result<String> deleteCourse(@PathVariable Long courseId) {
        teacherService.deleteCourse(courseId);
        return Result.ok("课程删除成功");
    }

    /**
     * 获取课程学习进度
     *
     * GET /teacher/courses/{courseId}/progress
     */
    @GetMapping("/courses/{courseId}/progress")
    public Result<Map<String, Object>> getCourseProgress(@PathVariable Long courseId) {
        Map<String, Object> data = teacherService.getCourseProgress(courseId);
        return Result.ok(data);
    }

    /**
     * 录入/修改成绩
     *
     * POST /teacher/courses/{courseId}/grades
     * Body: [ { "studentId": 1, "usualGrade": 85, "examGrade": 90, "experimentGrade": 88 }, ... ]
     */
    @PostMapping("/courses/{courseId}/grades")
    public Result<String> saveGrades(
            @PathVariable Long courseId,
            @RequestBody List<Map<String, Object>> grades) {
        int count = teacherService.saveGrades(courseId, grades);
        return Result.ok("成功录入 " + count + " 条成绩");
    }

    /**
     * 导出成绩（功能开发中）
     *
     * GET /teacher/courses/{courseId}/grades/export
     */
    @GetMapping("/courses/{courseId}/grades/export")
    public Result<String> exportGrades(@PathVariable Long courseId) {
        return Result.ok("功能开发中");
    }

    // ==================== 实验任务管理 ====================

    /**
     * 分页查询实验任务
     *
     * GET /teacher/tasks?taskType=EXPERIMENT&courseId=1&auditStatus=APPROVED&page=1&pageSize=10
     */
    @GetMapping("/tasks")
    public Result<IPage<Map<String, Object>>> getTasks(
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String auditStatus,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        IPage<Map<String, Object>> data = teacherService.getTasks(taskType, courseId, auditStatus, page, pageSize);
        return Result.ok(data);
    }

    /**
     * 创建实验任务
     *
     * POST /teacher/tasks
     * Body: { "projectName": "...", "description": "...", "projectType": "EXPERIMENT",
     *         "classId": 1, "startTime": "...", "endTime": "..." }
     */
    @PostMapping("/tasks")
    public Result<Map<String, Object>> createTask(@RequestBody Map<String, Object> taskData) {
        Map<String, Object> data = teacherService.createTask(taskData);
        return Result.ok("实验任务创建成功，待审核", data);
    }

    /**
     * 上传任务指导文件（功能开发中）
     *
     * POST /teacher/tasks/{taskId}/guide-files
     */
    @PostMapping("/tasks/{taskId}/guide-files")
    public Result<String> uploadGuideFile(@PathVariable Long taskId) {
        return Result.ok("功能开发中");
    }

    /**
     * 更新实验任务
     *
     * PUT /teacher/tasks/{taskId}
     */
    @PutMapping("/tasks/{taskId}")
    public Result<String> updateTask(
            @PathVariable Long taskId,
            @RequestBody Map<String, Object> taskData) {
        teacherService.updateTask(taskId, taskData);
        return Result.ok("实验任务更新成功");
    }

    /**
     * 删除实验任务
     *
     * DELETE /teacher/tasks/{taskId}
     */
    @DeleteMapping("/tasks/{taskId}")
    public Result<String> deleteTask(@PathVariable Long taskId) {
        teacherService.deleteTask(taskId);
        return Result.ok("实验任务删除成功");
    }

    /**
     * 获取实验任务提交列表
     *
     * GET /teacher/tasks/{taskId}/submissions?status=SUBMITTED&page=1&pageSize=10
     */
    @GetMapping("/tasks/{taskId}/submissions")
    public Result<Map<String, Object>> getTaskSubmissions(
            @PathVariable Long taskId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Map<String, Object> data = teacherService.getTaskSubmissions(taskId, status, page, pageSize);
        return Result.ok(data);
    }

    /**
     * 批阅提交（通过/退回）
     *
     * POST /teacher/submissions/{submissionId}/grade
     * Body: { "score": 85, "comment": "做得好", "action": "PASS" }
     */
    @PostMapping("/submissions/{submissionId}/grade")
    public Result<Map<String, Object>> gradeSubmission(
            @PathVariable Long submissionId,
            @RequestBody Map<String, Object> body) {
        Integer score = body.get("score") != null
                ? Integer.valueOf(body.get("score").toString()) : null;
        String comment = (String) body.get("comment");
        String action = (String) body.get("action");

        Map<String, Object> data = teacherService.gradeSubmission(submissionId, score, comment, action);
        return Result.ok("批阅完成", data);
    }

    /**
     * 退回提交（含退回原因）
     *
     * POST /teacher/submissions/{submissionId}/return
     * Body: { "returnReason": "需要补充实验数据" }
     */
    @PostMapping("/submissions/{submissionId}/return")
    public Result<Map<String, Object>> returnSubmission(
            @PathVariable Long submissionId,
            @RequestBody Map<String, Object> body) {
        String returnReason = (String) body.get("returnReason");
        Map<String, Object> data = teacherService.returnSubmission(submissionId, returnReason);
        return Result.ok("已退回", data);
    }

    // ==================== 教学资源管理 ====================

    /**
     * 分页查询教学资源
     *
     * GET /teacher/resources?auditStatus=APPROVED&type=pdf&keyword=xxx&page=1&pageSize=10
     */
    @GetMapping("/resources")
    public Result<IPage<Map<String, Object>>> getResources(
            @RequestParam(required = false) String auditStatus,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        IPage<Map<String, Object>> data = teacherService.getResources(auditStatus, type, keyword, page, pageSize);
        return Result.ok(data);
    }

    /**
     * 上传教学资源
     *
     * POST /teacher/resources
     * Body: { "resourceName": "...", "description": "...", "categoryId": 1,
     *         "fileUrl": "...", "fileName": "...", "fileType": "pdf",
     *         "fileSize": 1024, "visibility": "PUBLIC", "courseId": 1 }
     */
    @PostMapping("/resources")
    public Result<Map<String, Object>> uploadResource(@RequestBody Map<String, Object> resourceData) {
        Map<String, Object> data = teacherService.uploadResource(resourceData);
        return Result.ok("资源上传成功，待审核", data);
    }

    /**
     * 更新教学资源
     *
     * PUT /teacher/resources/{resourceId}
     */
    @PutMapping("/resources/{resourceId}")
    public Result<String> updateResource(
            @PathVariable Long resourceId,
            @RequestBody Map<String, Object> resourceData) {
        teacherService.updateResource(resourceId, resourceData);
        return Result.ok("资源更新成功");
    }

    /**
     * 删除教学资源
     *
     * DELETE /teacher/resources/{resourceId}
     */
    @DeleteMapping("/resources/{resourceId}")
    public Result<String> deleteResource(@PathVariable Long resourceId) {
        teacherService.deleteResource(resourceId);
        return Result.ok("资源删除成功");
    }

    /**
     * 查看审核反馈（功能开发中）
     *
     * GET /teacher/resources/{resourceId}/audit-feedback
     */
    @GetMapping("/resources/{resourceId}/audit-feedback")
    public Result<String> getAuditFeedback(@PathVariable Long resourceId) {
        return Result.ok("功能开发中");
    }

    /**
     * 重新提交审核（功能开发中）
     *
     * POST /teacher/resources/{resourceId}/resubmit
     * Body: { "resourceName": "...", "fileUrl": "..." }
     */
    @PostMapping("/resources/{resourceId}/resubmit")
    public Result<String> resubmitResource(
            @PathVariable Long resourceId,
            @RequestBody Map<String, Object> resourceData) {
        teacherService.resubmitResource(resourceId, resourceData);
        return Result.ok("已重新提交审核");
    }

    // ==================== 学情分析 ====================

    /**
     * 获取班级学情总览
     *
     * GET /teacher/analytics/class-overview?classId=1&semester=1
     */
    @GetMapping("/analytics/class-overview")
    public Result<Map<String, Object>> getClassOverview(
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) Long semester) {
        Map<String, Object> data = teacherService.getClassOverview(classId, semester);
        return Result.ok(data);
    }

    /**
     * 获取某个学生的学情分析（教师视角）
     *
     * GET /teacher/analytics/student/{userId}?semester=1
     */
    @GetMapping("/analytics/student/{userId}")
    public Result<Map<String, Object>> getStudentAnalytics(
            @PathVariable Long userId,
            @RequestParam(required = false) Long semester) {
        Map<String, Object> data = teacherService.getStudentAnalytics(userId, semester);
        return Result.ok(data);
    }

    /**
     * 获取预警学生列表
     *
     * GET /teacher/analytics/at-risk-students?classId=1&filterType=ALL
     */
    @GetMapping("/analytics/at-risk-students")
    public Result<List<Map<String, Object>>> getAtRiskStudents(
            @RequestParam(required = false) Long classId,
            @RequestParam(defaultValue = "ALL") String filterType) {
        List<Map<String, Object>> data = teacherService.getAtRiskStudents(classId, filterType);
        return Result.ok(data);
    }

    /**
     * 导出学情分析（功能开发中）
     *
     * GET /teacher/analytics/export?classId=1&semester=1
     */
    @GetMapping("/analytics/export")
    public Result<String> exportAnalytics(
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) Long semester) {
        return Result.ok("功能开发中");
    }

    // ==================== 通知公告 ====================

    /**
     * 发布班级通知
     *
     * POST /teacher/notices
     * Body: { "classId": 1, "title": "...", "content": "...", "importance": "NORMAL" }
     */
    @PostMapping("/notices")
    public Result<Map<String, Object>> createNotice(@RequestBody Map<String, Object> body) {
        Long classId = body.get("classId") != null
                ? Long.valueOf(body.get("classId").toString()) : null;
        String title = (String) body.get("title");
        String content = (String) body.get("content");
        String importance = (String) body.get("importance");

        Map<String, Object> data = teacherService.createNotice(classId, title, content, importance);
        return Result.ok("公告发布成功", data);
    }

    /**
     * 分页查询教师发布的公告
     *
     * GET /teacher/notices?classId=1&page=1&pageSize=10
     */
    @GetMapping("/notices")
    public Result<IPage<Map<String, Object>>> getNotices(
            @RequestParam(required = false) Long classId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        IPage<Map<String, Object>> data = teacherService.getNotices(classId, page, pageSize);
        return Result.ok(data);
    }

    /**
     * 删除公告
     *
     * DELETE /teacher/notices/{noticeId}
     */
    @DeleteMapping("/notices/{noticeId}")
    public Result<String> deleteNotice(@PathVariable Long noticeId) {
        teacherService.deleteNotice(noticeId);
        return Result.ok("公告删除成功");
    }

    // ==================== 消息 ====================

    /**
     * 分页查询教师消息
     *
     * GET /teacher/messages?page=1&pageSize=10
     */
    @GetMapping("/messages")
    public Result<Map<String, Object>> getTeacherMessages(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Map<String, Object> data = teacherService.getTeacherMessages(page, pageSize);
        return Result.ok(data);
    }

}
