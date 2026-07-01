package com.iotp.controller;

import com.iotp.common.Result;
import com.iotp.entity.*;
import com.iotp.mapper.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 公共接口控制器
 * <p>提供院系列表、班级列表、学期列表、课程下拉列表、文件上传等公共接口</p>
 */
@RestController
@RequestMapping("/common")
public class CommonController {

    /** 上传文件根目录，用 user.dir + uploads 保证绝对路径 */
    private static final String uploadDir = System.getProperty("user.dir") + java.io.File.separator + "uploads";

    @Autowired
    private SysDepartmentMapper departmentMapper;

    @Autowired
    private SysClassMapper classMapper;

    @Autowired
    private SysSemesterMapper semesterMapper;

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private SysConfigMapper sysConfigMapper;

    /** 从系统配置表读取字符串值 */
    private String getConfigValue(String key, String defaultValue) {
        try {
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysConfig> wrapper =
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
            wrapper.eq(SysConfig::getConfigKey, key);
            SysConfig cfg = sysConfigMapper.selectOne(wrapper);
            return (cfg != null && cfg.getConfigValue() != null) ? cfg.getConfigValue() : defaultValue;
        } catch (Exception e) { return defaultValue; }
    }

    /** 从系统配置表读取长整数值 */
    private long getConfigLong(String key, long defaultValue) {
        try {
            String val = getConfigValue(key, String.valueOf(defaultValue));
            return Long.parseLong(val);
        } catch (NumberFormatException e) { return defaultValue; }
    }

    /**
     * 6.2 获取院系列表
     */
    @GetMapping("/departments")
    public Result<List<Map<String, Object>>> getDepartments() {
        List<SysDepartment> depts = departmentMapper.selectList(null);
        List<Map<String, Object>> list = depts.stream()
                .filter(d -> d.getIsDeleted() == null || d.getIsDeleted() == 0)
                .map(d -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("departmentId", d.getId());
                    map.put("departmentName", d.getDeptName());
                    return map;
                })
                .collect(Collectors.toList());
        return Result.ok(list);
    }

    /**
     * 6.3 获取班级列表
     * @param departmentId 院系ID（可选）
     * @param keyword      班级名称搜索（可选）
     */
    @GetMapping("/classes")
    public Result<List<Map<String, Object>>> getClasses(
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) String keyword) {

        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysClass> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        wrapper.eq(SysClass::getIsDeleted, 0);

        if (departmentId != null) {
            wrapper.eq(SysClass::getDepartmentId, departmentId);
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(SysClass::getClassName, keyword);
        }

        List<SysClass> classes = classMapper.selectList(wrapper);
        List<Map<String, Object>> list = classes.stream().map(c -> {
            Map<String, Object> map = new HashMap<>();
            map.put("classId", c.getId());
            map.put("className", c.getClassName());
            map.put("classCode", c.getClassCode());
            map.put("departmentId", c.getDepartmentId());
            map.put("grade", c.getGrade());
            return map;
        }).collect(Collectors.toList());

        return Result.ok(list);
    }

    /**
     * 6.4 获取学期列表
     */
    @GetMapping("/semesters")
    public Result<List<Map<String, Object>>> getSemesters() {
        List<SysSemester> semesters = semesterMapper.selectList(null);
        List<Map<String, Object>> list = semesters.stream()
                .map(s -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("semesterId", s.getId());
                    map.put("semesterName", s.getSemesterName());
                    return map;
                })
                .collect(Collectors.toList());
        return Result.ok(list);
    }

    /**
     * 6.5 获取课程基础信息列表（下拉选择用）
     */
    @GetMapping("/courses")
    public Result<List<Map<String, Object>>> getCourseList() {
        List<Course> courses = courseMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Course>()
                        .eq(Course::getIsDeleted, 0)
                        .eq(Course::getStatus, 1)
        );
        List<Map<String, Object>> list = courses.stream().map(c -> {
            Map<String, Object> map = new HashMap<>();
            map.put("courseId", c.getId());
            map.put("courseName", c.getCourseName());
            map.put("courseCode", c.getCourseCode());
            return map;
        }).collect(Collectors.toList());
        return Result.ok(list);
    }

    /**
     * 6.6 获取当前系统学期配置
     */
    @GetMapping("/current-semester")
    public Result<Map<String, String>> getCurrentSemester() {
        // 从系统配置表读取当前学期
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysConfig> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        wrapper.eq(SysConfig::getConfigKey, "current_semester");
        SysConfig config = sysConfigMapper.selectOne(wrapper);

        String semester = (config != null && config.getConfigValue() != null)
                ? config.getConfigValue() : "";

        Map<String, String> data = new HashMap<>();
        data.put("semester", semester);
        return Result.ok(data);
    }

    /**
     * 6.1 文件上传（通用）
     * <p>支持头像、课件资料、资源、提交报告、指导文档等模块的文件上传</p>
     * @param file   上传的文件
     * @param module 业务模块标识：avatar / course_material / resource / submission / guide
     */
    @PostMapping("/upload")
    public Result<Map<String, Object>> upload(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam("module") String module,
            @RequestParam(value = "fileName", required = false) String customFileName) {

        if (file.isEmpty()) {
            return Result.badRequest("文件不能为空");
        }

        // 读取系统配置：文件大小限制 和 允许的文件类型
        long maxFileSize = getConfigLong("max_file_upload_size", 104857600L); // 默认100MB
        if (file.getSize() > maxFileSize) {
            return Result.error(400, "文件大小超过限制（最大 " + (maxFileSize / 1048576) + " MB）");
        }

        String allowedTypes = getConfigValue("allowed_file_types", "");
        if (allowedTypes != null && !allowedTypes.isEmpty()) {
            String originalName = file.getOriginalFilename();
            String ext = "";
            if (originalName != null && originalName.contains(".")) {
                ext = originalName.substring(originalName.lastIndexOf(".") + 1).toLowerCase();
            }
            if (!ext.isEmpty()) {
                java.util.Set<String> allowed = new java.util.HashSet<>(
                        java.util.Arrays.asList(allowedTypes.split(",")));
                if (!allowed.contains(ext)) {
                    return Result.error(400, "不支持的文件类型：" + ext + "（允许：" + allowedTypes + "）");
                }
            }
        }

        try {
            // 构造存储目录：{uploadDir}/{module}/YYYY/MM/
            java.time.LocalDate now = java.time.LocalDate.now();
            String yearMonth = now.getYear() + "/" + String.format("%02d", now.getMonthValue());
            String relativePath = module + "/" + yearMonth + "/";

            // 用 NIO 创建目录（绝对路径，不依赖 Tomcat 工作目录）
            Path uploadRoot = Paths.get(uploadDir).toAbsolutePath();
            Path dirPath = uploadRoot.resolve(relativePath);
            Files.createDirectories(dirPath);

            // 文件名：优先用自定义名，否则用原始名
            String originalName = file.getOriginalFilename();
            String displayName = (customFileName != null && !customFileName.isEmpty()) ? customFileName : originalName;
            String extension = "";
            String baseName = displayName;
            if (displayName != null && displayName.contains(".")) {
                extension = displayName.substring(displayName.lastIndexOf("."));
                baseName = displayName.substring(0, displayName.lastIndexOf("."));
            } else if (originalName != null && originalName.contains(".")) {
                extension = originalName.substring(originalName.lastIndexOf("."));
            }
            // 重名处理：文件名已存在则追加序号
            String finalName = displayName;
            Path filePath = dirPath.resolve(finalName);
            int seq = 1;
            while (Files.exists(filePath)) {
                finalName = baseName + "_" + seq + extension;
                filePath = dirPath.resolve(finalName);
                seq++;
            }

            // 保存文件
            file.transferTo(filePath.toFile());

            // 返回文件信息，URL 直接用文件名
            Map<String, Object> data = new HashMap<>();
            data.put("fileId", System.currentTimeMillis());
            data.put("fileName", displayName);
            data.put("fileSize", file.getSize());
            // 用查询参数传路径，彻底避免中文 URL 编码问题
            String encodedPath = java.net.URLEncoder.encode(relativePath + finalName, "UTF-8").replace("+", "%20");
            data.put("fileUrl", "/api/v1/common/file?path=" + encodedPath);
            data.put("uploadTime", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));

            return Result.ok("上传成功", data);
        } catch (Exception e) {
            return Result.serverError("文件上传失败：" + e.getMessage());
        }
    }

    /**
     * 6.7 访问上传的文件（头像、课件等）- 查询参数方式，避免中文路径编码问题
     * GET /common/file?path=guide/2026/06/文件名.pdf
     */
    @GetMapping("/file")
    public ResponseEntity<Resource> serveFileByPath(@RequestParam("path") String relativePath) {
        Path filePath = Paths.get(uploadDir).toAbsolutePath();
        for (String seg : relativePath.split("/")) {
            if (!seg.isEmpty()) filePath = filePath.resolve(seg);
        }
        Resource resource = new FileSystemResource(filePath);
        if (resource.exists() && resource.isReadable()) {
            String contentType = "application/octet-stream";
            boolean canPreview = false;
            String name = filePath.getFileName().toString().toLowerCase();
            if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
                contentType = "image/jpeg"; canPreview = true;
            } else if (name.endsWith(".png")) {
                contentType = "image/png"; canPreview = true;
            } else if (name.endsWith(".gif")) {
                contentType = "image/gif"; canPreview = true;
            } else if (name.endsWith(".pdf")) {
                contentType = "application/pdf"; canPreview = true;
            } else if (name.endsWith(".txt") || name.endsWith(".md")) {
                contentType = "text/plain;charset=UTF-8"; canPreview = true;
            } else if (name.endsWith(".doc")) {
                contentType = "application/msword";
            } else if (name.endsWith(".docx")) {
                contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            } else if (name.endsWith(".xls")) {
                contentType = "application/vnd.ms-excel";
            } else if (name.endsWith(".xlsx")) {
                contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            } else if (name.endsWith(".ppt")) {
                contentType = "application/vnd.ms-powerpoint";
            } else if (name.endsWith(".pptx")) {
                contentType = "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            }
            ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType));
            if (!canPreview) {
                try {
                    builder.header("Content-Disposition",
                        "attachment; filename*=UTF-8''" + java.net.URLEncoder.encode(name, "UTF-8").replace("+", "%20"));
                } catch (Exception ignored) {}
            }
            return builder.body(resource);
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * 6.7 old /files/** — 保留，兼容旧 URL
     */
    @GetMapping("/files/**")
    public ResponseEntity<Resource> serveFile(HttpServletRequest request) {
        // 提取 /common/files/ 之后的路径部分
        String requestURI = request.getRequestURI();
        String contextPath = request.getContextPath();
        String path = requestURI.substring(contextPath.length());
        String prefix = "/common/files/";
        if (!path.startsWith(prefix)) {
            return ResponseEntity.notFound().build();
        }
        String fullPath = path.substring(prefix.length());

        Path filePath = Paths.get(uploadDir).toAbsolutePath();
        for (String seg : fullPath.split("/")) {
            if (!seg.isEmpty()) filePath = filePath.resolve(seg);
        }
        Resource resource = new FileSystemResource(filePath);
        if (resource.exists() && resource.isReadable()) {
            String contentType = "application/octet-stream";
            boolean canPreview = false;
            String name = filePath.getFileName().toString().toLowerCase();
            if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
                contentType = "image/jpeg"; canPreview = true;
            } else if (name.endsWith(".png")) {
                contentType = "image/png"; canPreview = true;
            } else if (name.endsWith(".gif")) {
                contentType = "image/gif"; canPreview = true;
            } else if (name.endsWith(".pdf")) {
                contentType = "application/pdf"; canPreview = true;
            } else if (name.endsWith(".txt") || name.endsWith(".md")) {
                contentType = "text/plain;charset=UTF-8"; canPreview = true;
            } else if (name.endsWith(".doc")) {
                contentType = "application/msword";
            } else if (name.endsWith(".docx")) {
                contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            } else if (name.endsWith(".xls")) {
                contentType = "application/vnd.ms-excel";
            } else if (name.endsWith(".xlsx")) {
                contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            } else if (name.endsWith(".ppt")) {
                contentType = "application/vnd.ms-powerpoint";
            } else if (name.endsWith(".pptx")) {
                contentType = "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            }
            ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType));
            if (!canPreview) {
                // 不能在线预览的文件强制下载
                String downloadName = name;
                try {
                    builder.header("Content-Disposition",
                        "attachment; filename*=UTF-8''" + java.net.URLEncoder.encode(downloadName, "UTF-8").replace("+", "%20"));
                } catch (Exception ignored) {}
            }
            return builder.body(resource);
        }
        return ResponseEntity.notFound().build();
    }
}
