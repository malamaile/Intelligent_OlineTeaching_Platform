package com.iotp.controller;

import com.iotp.common.Result;
import com.iotp.entity.*;
import com.iotp.mapper.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 公共接口控制器
 * <p>提供院系列表、班级列表、学期列表、课程下拉列表、文件上传等公共接口</p>
 */
@RestController
@RequestMapping("/common")
public class CommonController {

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
    public Result<List<String>> getSemesters() {
        List<SysSemester> semesters = semesterMapper.selectList(null);
        List<String> list = semesters.stream()
                .map(SysSemester::getSemesterName)
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
            @RequestParam("module") String module) {

        if (file.isEmpty()) {
            return Result.badRequest("文件不能为空");
        }

        try {
            // 构造存储目录：files/{module}/YYYY/MM/
            java.time.LocalDate now = java.time.LocalDate.now();
            String yearMonth = now.getYear() + "/" + String.format("%02d", now.getMonthValue());
            String dirPath = "files/" + module + "/" + yearMonth + "/";

            // 创建目录
            java.io.File dir = new java.io.File(dirPath);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            // 生成唯一文件名
            String originalName = file.getOriginalFilename();
            String extension = "";
            if (originalName != null && originalName.contains(".")) {
                extension = originalName.substring(originalName.lastIndexOf("."));
            }
            String uniqueName = UUID.randomUUID().toString().replace("-", "") + extension;
            String fullPath = dirPath + uniqueName;

            // 保存文件
            file.transferTo(new java.io.File(fullPath));

            // 返回文件信息
            Map<String, Object> data = new HashMap<>();
            data.put("fileId", System.currentTimeMillis()); // 简化处理，实际应存储文件记录
            data.put("fileName", originalName);
            data.put("fileSize", file.getSize());
            data.put("fileUrl", "/" + fullPath);
            data.put("uploadTime", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));

            return Result.ok("上传成功", data);
        } catch (Exception e) {
            return Result.serverError("文件上传失败：" + e.getMessage());
        }
    }
}
