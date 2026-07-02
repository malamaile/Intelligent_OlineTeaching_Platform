package com.iotp.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.iotp.common.Result;
import com.iotp.security.UserContext;
import com.iotp.service.StudentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

/**
 * 学生端控制器
 * <p>提供学生首页看板、课程学习、实验任务、教学资源、学情分析、成绩查询、
 * 消息通知等 RESTful API 接口。</p>
 */
@RestController
@RequestMapping("/student")
public class StudentController {

    private static final Logger log = LoggerFactory.getLogger(StudentController.class);

    @Autowired
    private StudentService studentService;

    /** 文件上传根目录 */
    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    // ==================== 首页看板 ====================

    /**
     * 获取学生首页看板数据
     *
     * GET /student/dashboard
     */
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> getDashboard() {
        Map<String, Object> data = studentService.getDashboard();
        return Result.ok(data);
    }

    // ==================== 课程学习 ====================

    /**
     * 分页查询学生课程列表
     *
     * GET /student/courses?keyword=xxx&semester=1&page=1&pageSize=10
     */
    @GetMapping("/courses")
    public Result<IPage<Map<String, Object>>> getCourses(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long semester,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        IPage<Map<String, Object>> data = studentService.getCourses(keyword, semester, page, pageSize);
        return Result.ok(data);
    }

    /**
     * 获取课程详情（含章节及学习状态）
     *
     * GET /student/courses/{courseId}
     */
    @GetMapping("/courses/{courseId}")
    public Result<Map<String, Object>> getCourseDetail(@PathVariable Long courseId) {
        Map<String, Object> data = studentService.getCourseDetail(courseId);
        return Result.ok(data);
    }

    /**
     * 更新学习进度
     *
     * POST /student/courses/{courseId}/progress
     * Body: { "chapterId": 1, "position": 300, "duration": 30 }
     */
    @PostMapping("/courses/{courseId}/progress")
    public Result<Map<String, Object>> updateProgress(
            @PathVariable Long courseId,
            @RequestBody Map<String, Object> body) {
        Long chapterId = body.get("chapterId") != null
                ? Long.valueOf(body.get("chapterId").toString()) : null;
        Integer position = body.get("position") != null
                ? Integer.valueOf(body.get("position").toString()) : 0;
        Integer duration = body.get("duration") != null
                ? Integer.valueOf(body.get("duration").toString()) : 0;
        Integer pageDuration = body.get("pageDuration") != null
                ? Integer.valueOf(body.get("pageDuration").toString()) : 0;

        Map<String, Object> data = studentService.updateProgress(courseId, chapterId, position, duration, pageDuration);
        return Result.ok(data);
    }

    /**
     * 通过邀请码加入课程
     *
     * POST /student/courses/join-by-invite
     * Body: { "inviteCode": "4827" }
     */
    @PostMapping("/courses/join-by-invite")
    public Result<Map<String, Object>> joinByInviteCode(@RequestBody Map<String, Object> body) {
        String inviteCode = (String) body.get("inviteCode");
        if (inviteCode == null || inviteCode.trim().isEmpty()) {
            return Result.badRequest("邀请码不能为空");
        }
        Map<String, Object> data = studentService.joinByInviteCode(inviteCode.trim());
        return Result.ok("加入成功", data);
    }

    /**
     * 通过邀请码加入课程
     *
     * POST /student/courses/join-by-invite
     * Body: { "inviteCode": "4827" }
     */
    @PostMapping("/courses/join-by-invite")
    public Result<Map<String, Object>> joinByInviteCode(@RequestBody Map<String, Object> body) {
        String inviteCode = (String) body.get("inviteCode");
        if (inviteCode == null || inviteCode.trim().isEmpty()) {
            return Result.badRequest("邀请码不能为空");
        }
        Map<String, Object> data = studentService.joinByInviteCode(inviteCode.trim());
        return Result.ok("加入成功", data);
    }

    /**
     * 下载课程资料（章节附件）
     *
     * GET /student/courses/{courseId}/materials/{materialId}/download
     * materialId 对应章节 ID
     */
    @GetMapping("/courses/{courseId}/materials/{materialId}/download")
    public void downloadMaterial(
            @PathVariable Long courseId,
            @PathVariable Long materialId,
            javax.servlet.http.HttpServletResponse response) {
        Object[] result = studentService.downloadMaterial(courseId, materialId);
        String fileName = (String) result[0];
        byte[] fileBytes = (byte[]) result[1];

        try {
            response.setContentType("application/octet-stream");
            response.setHeader("Content-Disposition",
                    "attachment; filename*=UTF-8''" + java.net.URLEncoder.encode(fileName, "UTF-8").replace("+", "%20"));
            response.setContentLength(fileBytes.length);
            response.getOutputStream().write(fileBytes);
            response.getOutputStream().flush();
        } catch (java.io.IOException e) {
            log.error("课程资料下载失败：{}", e.getMessage(), e);
            throw new RuntimeException("文件下载失败", e);
        }
    }

    // ==================== 实验任务 ====================

    /**
     * 分页查询实验任务列表
     *
     * GET /student/tasks?type=xxx&status=SUBMITTED&page=1&pageSize=10
     */
    @GetMapping("/tasks")
    public Result<IPage<Map<String, Object>>> getTasks(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        IPage<Map<String, Object>> data = studentService.getTasks(type, status, page, pageSize);
        return Result.ok(data);
    }

    /**
     * 获取实验任务详情
     *
     * GET /student/tasks/{taskId}
     */
    @GetMapping("/tasks/{taskId}")
    public Result<Map<String, Object>> getTaskDetail(@PathVariable Long taskId) {
        Map<String, Object> data = studentService.getTaskDetail(taskId);
        return Result.ok(data);
    }

    /**
     * 提交实验任务（支持文件上传）
     *
     * POST /student/tasks/{taskId}/submit
     * 格式：multipart/form-data，字段：content（过程描述）、reportFile（实验报告文件）
     */
    @PostMapping("/tasks/{taskId}/submit")
    public Result<Map<String, Object>> submitTask(
            @PathVariable Long taskId,
            @RequestParam(required = false) String content,
            @RequestParam(required = false) MultipartFile reportFile) {
        // 处理文件上传
        String reportFileUrl = handleFileUpload(reportFile, "tasks");

        Map<String, Object> data = studentService.submitTask(taskId, content, reportFileUrl);
        return Result.ok("提交成功", data);
    }

    /**
     * 重新提交实验任务
     *
     * POST /student/tasks/{taskId}/resubmit
     * 格式：multipart/form-data，字段：content（过程描述）、reportFile（实验报告文件）
     */
    @PostMapping("/tasks/{taskId}/resubmit")
    public Result<Map<String, Object>> resubmitTask(
            @PathVariable Long taskId,
            @RequestParam(required = false) String content,
            @RequestParam(required = false) MultipartFile reportFile) {
        // 处理文件上传
        String reportFileUrl = handleFileUpload(reportFile, "tasks");

        Map<String, Object> data = studentService.resubmitTask(taskId, content, reportFileUrl);
        return Result.ok("重新提交成功", data);
    }

    /**
     * 获取实验任务成绩
     *
     * GET /student/tasks/{taskId}/result
     */
    @GetMapping("/tasks/{taskId}/result")
    public Result<Map<String, Object>> getTaskResult(@PathVariable Long taskId) {
        Map<String, Object> data = studentService.getTaskResult(taskId);
        return Result.ok(data);
    }

    // ==================== 教学资源 ====================

    /**
     * 分页查询教学资源
     *
     * GET /student/resources?keyword=xxx&category=1&courseId=1&scope=PUBLIC&page=1&pageSize=10
     */
    @GetMapping("/resources")
    public Result<IPage<Map<String, Object>>> getResources(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long category,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String scope,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        IPage<Map<String, Object>> data = studentService.getResources(keyword, category, courseId, scope, page, pageSize);
        return Result.ok(data);
    }

    /**
     * 预览教学资源（功能开发中）
     *
     * GET /student/resources/{resourceId}/preview
     */
    @GetMapping("/resources/{resourceId}/preview")
    public Result<String> previewResource(@PathVariable Long resourceId) {
        return Result.ok("功能开发中");
    }

    /**
     * 下载教学资源
     *
     * GET /student/resources/{resourceId}/download
     */
    @GetMapping("/resources/{resourceId}/download")
    public void downloadResource(@PathVariable Long resourceId, javax.servlet.http.HttpServletResponse response) {
        Object[] result = studentService.downloadResource(resourceId);
        String fileName = (String) result[0];
        byte[] fileBytes = (byte[]) result[1];

        try {
            response.setContentType("application/octet-stream");
            response.setHeader("Content-Disposition",
                    "attachment; filename*=UTF-8''" + java.net.URLEncoder.encode(fileName, "UTF-8").replace("+", "%20"));
            response.setContentLength(fileBytes.length);
            response.getOutputStream().write(fileBytes);
            response.getOutputStream().flush();
        } catch (java.io.IOException e) {
            log.error("文件下载失败：{}", e.getMessage(), e);
            throw new RuntimeException("文件下载失败", e);
        }
    }

    /**
     * 收藏教学资源
     *
     * POST /student/resources/{resourceId}/favorite
     */
    @PostMapping("/resources/{resourceId}/favorite")
    public Result<Map<String, Object>> favoriteResource(@PathVariable Long resourceId) {
        Map<String, Object> data = studentService.favoriteResource(resourceId);
        return Result.ok("收藏成功", data);
    }

    /**
     * 取消收藏教学资源
     *
     * DELETE /student/resources/{resourceId}/favorite
     */
    @DeleteMapping("/resources/{resourceId}/favorite")
    public Result<String> unfavoriteResource(@PathVariable Long resourceId) {
        // 调用切换式收藏方法确保取消收藏
        studentService.favoriteResource(resourceId);
        return Result.ok("已取消收藏");
    }

    // ==================== 学情分析 ====================

    /**
     * 获取学情分析总览
     *
     * GET /student/analytics/overview?semester=1
     */
    @GetMapping("/analytics/overview")
    public Result<Map<String, Object>> getAnalyticsOverview(
            @RequestParam(required = false) Long semester) {
        Map<String, Object> data = studentService.getAnalyticsOverview(semester);
        return Result.ok(data);
    }

    /**
     * 获取学业诊断报告
     *
     * GET /student/analytics/diagnosis?semester=1
     */
    @GetMapping("/analytics/diagnosis")
    public Result<Map<String, Object>> getDiagnosis(
            @RequestParam(required = false) Long semester) {
        Map<String, Object> data = studentService.getDiagnosis(semester);
        return Result.ok(data);
    }

    // ==================== 个人信息 ====================

    /**
     * 更新个人资料
     *
     * PUT /student/profile
     * Body: { "avatar": "...", "realName": "...", "phone": "...", "email": "..." }
     */
    @PutMapping("/profile")
    public Result<String> updateProfile(@RequestBody Map<String, Object> body) {
        Long userId = UserContext.getUserId();
        studentService.updateProfile(userId, body);
        return Result.ok("个人资料更新成功");
    }

    /**
     * 上传头像
     *
     * POST /student/profile/avatar
     * 格式：multipart/form-data，字段：file（头像文件）
     */
    @PostMapping("/profile/avatar")
    public Result<Map<String, Object>> uploadAvatar(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "avatar", required = false) MultipartFile avatarFile) {
        // 兼容前端两种字段名：file / avatar
        MultipartFile uploadFile = file != null && !file.isEmpty() ? file : avatarFile;
        if (uploadFile == null || uploadFile.isEmpty()) {
            return Result.badRequest("头像文件不能为空");
        }

        String fileUrl = handleFileUpload(uploadFile, "avatars");
        if (fileUrl == null) {
            return Result.serverError("头像上传失败");
        }

        // 持久化头像 URL 到数据库
        Long userId = UserContext.getUserId();
        java.util.Map<String, Object> updates = new java.util.LinkedHashMap<>();
        updates.put("avatar", fileUrl);
        studentService.updateProfile(userId, updates);

        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("avatarUrl", fileUrl);
        return Result.ok("头像上传成功", data);
    }

    // ==================== 成绩查询 ====================

    /**
     * 分页查询成绩
     *
     * GET /student/grades?semester=1&page=1&pageSize=10
     */
    @GetMapping("/grades")
    public Result<IPage<Map<String, Object>>> getGrades(
            @RequestParam(required = false) Long semester,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        IPage<Map<String, Object>> data = studentService.getGrades(semester, page, pageSize);
        return Result.ok(data);
    }

    // ==================== 消息通知 ====================

    /**
     * 分页查询消息列表
     *
     * GET /student/messages?type=SYSTEM&isRead=0&page=1&pageSize=10
     */
    @GetMapping("/messages")
    public Result<Map<String, Object>> getMessages(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer isRead,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Map<String, Object> data = studentService.getMessages(type, isRead, page, pageSize);
        return Result.ok(data);
    }

    /**
     * 标记单条消息为已读
     *
     * PUT /student/messages/{messageId}/read
     */
    @PutMapping("/messages/{messageId}/read")
    public Result<String> markMessageRead(@PathVariable Long messageId) {
        studentService.markMessageRead(messageId);
        return Result.ok("已标记为已读");
    }

    /**
     * 标记所有消息为已读
     *
     * PUT /student/messages/read-all
     */
    @PutMapping("/messages/read-all")
    public Result<String> markAllMessagesRead() {
        studentService.markAllMessagesRead();
        return Result.ok("所有消息已标记为已读");
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 处理文件上传，返回可访问的 URL
     *
     * @param file   上传的文件
     * @param subDir 子目录名称（如 tasks, avatars）
     * @return 文件访问 URL，上传失败或文件为空时返回 null
     */
    private String handleFileUpload(MultipartFile file, String subDir) {
        if (file == null || file.isEmpty()) {
            return null;
        }

        try {
            // 生成唯一文件名
            String originalFilename = file.getOriginalFilename();
            String ext = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                ext = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String filename = UUID.randomUUID().toString().replace("-", "") + ext;

            // 确保目录存在
            Path uploadPath = Paths.get(uploadDir, subDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            // 保存文件
            Path filePath = uploadPath.resolve(filename);
            Files.copy(file.getInputStream(), filePath);

            // 返回文件访问 URL（通过 CommonController 端点）
            return "/api/v1/common/files/" + subDir + "/" + filename;
        } catch (IOException e) {
            log.error("文件上传失败：{}", e.getMessage(), e);
            return null;
        }
    }

}
