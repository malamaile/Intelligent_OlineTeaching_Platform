package com.iotp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iotp.common.BusinessException;
import com.iotp.entity.*;
import com.iotp.mapper.*;
import com.iotp.security.UserContext;
import com.iotp.service.TeacherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 教师端业务服务实现
 */
@Service
public class TeacherServiceImpl implements TeacherService {

    private static final Logger log = LoggerFactory.getLogger(TeacherServiceImpl.class);

    /** 审核状态常量 */
    private static final String AUDIT_PENDING = "PENDING";
    private static final String AUDIT_APPROVED = "APPROVED";
    private static final String AUDIT_REJECTED = "REJECTED";

    /** 提交状态常量 */
    private static final String SUBMIT_SUBMITTED = "SUBMITTED";
    private static final String SUBMIT_GRADED = "GRADED";
    private static final String SUBMIT_RETURNED = "RETURNED";

    // ==================== 注入 Mapper ====================

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private CoursePlanMapper coursePlanMapper;

    @Autowired
    private CourseChapterMapper courseChapterMapper;

    @Autowired
    private StudentCourseEnrollmentMapper enrollmentMapper;

    @Autowired
    private StudentLearningProgressMapper learningProgressMapper;

    @Autowired
    private ExperimentTaskMapper experimentTaskMapper;

    @Autowired
    private ExperimentProjectMapper experimentProjectMapper;

    @Autowired
    private StudentExperimentSubmissionMapper submissionMapper;

    @Autowired
    private StudentGradeMapper studentGradeMapper;

    @Autowired
    private TeachingResourceMapper teachingResourceMapper;

    @Autowired
    private ResourceCategoryMapper resourceCategoryMapper;

    @Autowired
    private SysAnnouncementMapper sysAnnouncementMapper;

    @Autowired
    private SysMessageMapper sysMessageMapper;

    @Autowired
    private SysAuditLogMapper sysAuditLogMapper;

    @Autowired
    private SysClassMapper sysClassMapper;

    @Autowired
    private SysSemesterMapper sysSemesterMapper;

    @Autowired
    private SysDepartmentMapper sysDepartmentMapper;

    // ==================== 首页看板 ====================

    @Override
    public Map<String, Object> getDashboard() {
        Long teacherId = UserContext.getUserId();

        // 1. 待批阅统计：查询该教师发布的实验任务，统计学生提交状态为 SUBMITTED 的记录
        Map<String, Object> pendingReviews = new LinkedHashMap<>();

        // 1.1 查询该教师所有的实验任务 ID
        QueryWrapper<ExperimentTask> taskQw = new QueryWrapper<>();
        taskQw.eq("teacher_id", teacherId);
        List<ExperimentTask> myTasks = experimentTaskMapper.selectList(taskQw);
        Set<Long> taskIds = myTasks.stream().map(ExperimentTask::getId).collect(Collectors.toSet());

        long experiments = 0; // 实验待批阅
        long trainings = 0;   // 实训待批阅

        if (!taskIds.isEmpty()) {
            // 查询所有提交状态为 SUBMITTED 的记录
            QueryWrapper<StudentExperimentSubmission> subQw = new QueryWrapper<>();
            subQw.in("task_id", taskIds)
                    .eq("status", SUBMIT_SUBMITTED);
            List<StudentExperimentSubmission> pendingSubs = submissionMapper.selectList(subQw);

            for (StudentExperimentSubmission sub : pendingSubs) {
                // 通过任务获取项目类型
                ExperimentTask task = experimentTaskMapper.selectById(sub.getTaskId());
                if (task != null) {
                    ExperimentProject project = experimentProjectMapper.selectById(task.getProjectId());
                    if (project != null && "TRAINING".equals(project.getProjectType())) {
                        trainings++;
                    } else {
                        experiments++;
                    }
                }
            }
        }

        pendingReviews.put("experiments", experiments);
        pendingReviews.put("trainings", trainings);
        pendingReviews.put("total", experiments + trainings);

        // 1.2 构建待批阅详情列表（用于工作台直接展示）
        List<Map<String, Object>> pendingReviewItems = new ArrayList<>();
        if (!taskIds.isEmpty()) {
            QueryWrapper<StudentExperimentSubmission> detailSubQw = new QueryWrapper<>();
            detailSubQw.in("task_id", taskIds).eq("status", SUBMIT_SUBMITTED).orderByAsc("submit_time");
            List<StudentExperimentSubmission> detailSubs = submissionMapper.selectList(detailSubQw);
            Map<Long, List<StudentExperimentSubmission>> groupedByTask = new LinkedHashMap<>();
            for (StudentExperimentSubmission sub : detailSubs) {
                groupedByTask.computeIfAbsent(sub.getTaskId(), k -> new ArrayList<>()).add(sub);
            }
            for (Map.Entry<Long, List<StudentExperimentSubmission>> entry : groupedByTask.entrySet()) {
                Long tid = entry.getKey();
                List<StudentExperimentSubmission> subs = entry.getValue();
                ExperimentTask task = experimentTaskMapper.selectById(tid);
                if (task == null) continue;
                ExperimentProject project = experimentProjectMapper.selectById(task.getProjectId());
                String taskTitle = project != null ? project.getProjectName() : "";
                String taskType = project != null ? project.getProjectType() : "EXPERIMENT";
                String courseName = "";
                if (task.getCourseId() != null) { Course c = courseMapper.selectById(task.getCourseId()); courseName = c != null ? c.getCourseName() : ""; }
                String className = getClassName(task.getClassId());
                StudentExperimentSubmission firstSub = subs.get(0);
                SysUser firstStudent = sysUserMapper.selectById(firstSub.getStudentId());
                String studentName = firstStudent != null ? (firstStudent.getRealName() != null ? firstStudent.getRealName() : firstStudent.getUsername()) : "";
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("taskId", tid);
                item.put("taskTitle", taskTitle);
                item.put("taskType", taskType);
                item.put("courseName", courseName);
                item.put("className", className);
                item.put("pendingCount", subs.size());
                item.put("sampleStudentName", studentName);
                item.put("sampleSubmissionId", firstSub.getId());
                pendingReviewItems.add(item);
            }
        }

        // 2. 待审核统计：课程 / 实验任务 / 教学资源
        Map<String, Object> pendingAudits = new LinkedHashMap<>();

        // 2.1 待审核开课计划
        QueryWrapper<CoursePlan> planAuditQw = new QueryWrapper<>();
        planAuditQw.eq("teacher_id", teacherId)
                .eq("audit_status", AUDIT_PENDING);
        long pendingCoursePlans = coursePlanMapper.selectCount(planAuditQw);
        pendingAudits.put("courses", pendingCoursePlans);

        // 2.2 待审核实验任务
        QueryWrapper<ExperimentTask> taskAuditQw = new QueryWrapper<>();
        taskAuditQw.eq("teacher_id", teacherId)
                .eq("audit_status", AUDIT_PENDING);
        long pendingTasksCount = experimentTaskMapper.selectCount(taskAuditQw);
        pendingAudits.put("tasks", pendingTasksCount);

        // 2.3 待审核教学资源
        QueryWrapper<TeachingResource> resAuditQw = new QueryWrapper<>();
        resAuditQw.eq("teacher_id", teacherId)
                .eq("audit_status", AUDIT_PENDING);
        long pendingResourcesCount = teachingResourceMapper.selectCount(resAuditQw);
        pendingAudits.put("resources", pendingResourcesCount);
        pendingAudits.put("total", pendingCoursePlans + pendingTasksCount + pendingResourcesCount);

        // 3. 预警学生（按学生聚合，统计缺失任务数）
        List<Map<String, Object>> atRiskStudents = new ArrayList<>();
        if (!taskIds.isEmpty()) {
            // 按学生ID聚合缺失任务数
            Map<Long, Map<String, Object>> studentRiskMap = new LinkedHashMap<>();
            for (ExperimentTask task : myTasks) {
                if (task.getClassId() == null) continue;

                QueryWrapper<SysUser> studentQw = new QueryWrapper<>();
                studentQw.eq("class_id", task.getClassId());
                List<SysUser> students = sysUserMapper.selectList(studentQw);

                for (SysUser student : students) {
                    QueryWrapper<StudentExperimentSubmission> checkSub = new QueryWrapper<>();
                    checkSub.eq("task_id", task.getId())
                            .eq("student_id", student.getId());
                    StudentExperimentSubmission sub = submissionMapper.selectOne(checkSub);

                    if (sub == null || SUBMIT_RETURNED.equals(sub.getStatus())) {
                        Map<String, Object> risk = studentRiskMap.get(student.getId());
                        if (risk == null) {
                            risk = new LinkedHashMap<>();
                            risk.put("userId", student.getId());
                            risk.put("userName", student.getRealName() != null ? student.getRealName() : student.getUsername());
                            risk.put("className", getClassName(student.getClassId()));
                            risk.put("missedTasks", 0);
                            studentRiskMap.put(student.getId(), risk);
                        }
                        risk.put("missedTasks", (int) risk.get("missedTasks") + 1);
                        risk.put("reason", "有 " + risk.get("missedTasks") + " 个实验任务未提交或已被退回");
                    }
                }
            }
            atRiskStudents = new ArrayList<>(studentRiskMap.values());
            // 限制最大条数
            if (atRiskStudents.size() > 10) {
                atRiskStudents = atRiskStudents.subList(0, 10);
            }
        }

        // 4. 最近通知（审核结果通知给该教师的消息）
        List<Map<String, Object>> notifications = new ArrayList<>();
        QueryWrapper<SysMessage> msgQw = new QueryWrapper<>();
        msgQw.eq("receiver_id", teacherId)
                .orderByDesc("create_time")
                .last("LIMIT 10");
        List<SysMessage> messages = sysMessageMapper.selectList(msgQw);
        for (SysMessage msg : messages) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", msg.getId());
            // 消息类型映射
            String msgType = msg.getMessageType();
            if ("GRADE".equals(msgType) || "AUDIT_RESULT".equals(msgType)) {
                item.put("type", "AUDIT_RESULT");
            } else {
                item.put("type", "SYSTEM");
            }
            item.put("title", msg.getTitle());
            item.put("content", msg.getContent());
            item.put("isRead", msg.getIsRead());
            item.put("publishTime", msg.getCreateTime());
            notifications.add(item);
        }

        // 5. 班级概况
        Map<String, Object> classSummary = new LinkedHashMap<>();
        QueryWrapper<CoursePlan> myPlanQw = new QueryWrapper<>();
        myPlanQw.eq("teacher_id", teacherId)
                .eq("audit_status", AUDIT_APPROVED);
        List<CoursePlan> myPlans = coursePlanMapper.selectList(myPlanQw);
        Set<Long> classIds = myPlans.stream()
                .map(CoursePlan::getClassId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        long totalStudents = 0;
        BigDecimal avgCompletionRate = BigDecimal.ZERO;
        BigDecimal avgScore = BigDecimal.ZERO;
        int scoreCount = 0;

        if (!classIds.isEmpty()) {
            QueryWrapper<SysUser> stuQw = new QueryWrapper<>();
            stuQw.in("class_id", classIds);
            totalStudents = sysUserMapper.selectCount(stuQw);

            List<StudentCourseEnrollment> allEnrollments = new ArrayList<>();
            for (CoursePlan plan : myPlans) {
                QueryWrapper<StudentCourseEnrollment> enrollQw = new QueryWrapper<>();
                enrollQw.eq("course_plan_id", plan.getId());
                allEnrollments.addAll(enrollmentMapper.selectList(enrollQw));
            }
            if (!allEnrollments.isEmpty()) {
                BigDecimal sumProgress = allEnrollments.stream()
                        .map(e -> e.getProgressPercent() != null ? e.getProgressPercent() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                avgCompletionRate = sumProgress.divide(BigDecimal.valueOf(allEnrollments.size()), 2, RoundingMode.HALF_UP);
            }

            for (CoursePlan plan : myPlans) {
                QueryWrapper<StudentGrade> gradeQw = new QueryWrapper<>();
                gradeQw.eq("course_plan_id", plan.getId())
                        .eq("is_published", 1);
                List<StudentGrade> grades = studentGradeMapper.selectList(gradeQw);
                for (StudentGrade g : grades) {
                    if (g.getFinalGrade() != null) {
                        avgScore = avgScore.add(g.getFinalGrade());
                        scoreCount++;
                    }
                }
            }
            if (scoreCount > 0) {
                avgScore = avgScore.divide(BigDecimal.valueOf(scoreCount), 2, RoundingMode.HALF_UP);
            }
        }

        classSummary.put("totalStudents", totalStudents);
        classSummary.put("avgCompletionRate", avgCompletionRate);
        classSummary.put("avgScore", avgScore);
        classSummary.put("classCount", classIds.size());

        // 组装结果
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pendingReviews", pendingReviews);
        result.put("pendingReviewItems", pendingReviewItems);
        result.put("pendingAudits", pendingAudits);
        result.put("atRiskStudents", atRiskStudents);
        result.put("notifications", notifications);
        result.put("classSummary", classSummary);

        return result;
    }

    // ==================== 课程管理 ====================

    @Override
    public IPage<Map<String, Object>> getCourses(Long semester, String auditStatus, String keyword,
                                                  Integer page, Integer pageSize) {
        Long teacherId = UserContext.getUserId();

        Page<CoursePlan> planPage = new Page<>(page, pageSize);
        QueryWrapper<CoursePlan> planQw = new QueryWrapper<>();
        planQw.eq("teacher_id", teacherId);

        if (semester != null) {
            planQw.eq("semester_id", semester);
        }
        if (auditStatus != null && !auditStatus.isEmpty()) {
            planQw.eq("audit_status", auditStatus);
        }
        planQw.orderByDesc("create_time");

        IPage<CoursePlan> planPageResult = coursePlanMapper.selectPage(planPage, planQw);
        List<CoursePlan> plans = planPageResult.getRecords();

        List<Map<String, Object>> courseList = new ArrayList<>();
        for (CoursePlan plan : plans) {
            Course course = courseMapper.selectById(plan.getCourseId());
            if (course == null) {
                continue;
            }

            // 关键字过滤
            if (keyword != null && !keyword.isEmpty()
                    && !course.getCourseName().contains(keyword)) {
                continue;
            }

            SysClass sysClass = plan.getClassId() != null ? sysClassMapper.selectById(plan.getClassId()) : null;
            SysSemester sem = plan.getSemesterId() != null ? sysSemesterMapper.selectById(plan.getSemesterId()) : null;

            // 查询该计划的学生数和平均进度
            QueryWrapper<StudentCourseEnrollment> enrollCountQw = new QueryWrapper<>();
            enrollCountQw.eq("course_plan_id", plan.getId());
            List<StudentCourseEnrollment> enrollments = enrollmentMapper.selectList(enrollCountQw);
            long studentCount = enrollments.size();
            BigDecimal progress = BigDecimal.ZERO;
            if (!enrollments.isEmpty()) {
                BigDecimal sum = enrollments.stream()
                        .map(e -> e.getProgressPercent() != null ? e.getProgressPercent() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                progress = sum.divide(BigDecimal.valueOf(enrollments.size()), 2, RoundingMode.HALF_UP);
            }

            // 从 scheduleInfo 解析课时信息
            int totalHours = 48;
            int weeklyHours = 4;
            if (plan.getScheduleInfo() != null) {
                try {
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    Map<String, Object> si = mapper.readValue(plan.getScheduleInfo(), Map.class);
                    if (si != null) {
                        if (si.get("totalHours") != null) totalHours = Integer.parseInt(si.get("totalHours").toString());
                        if (si.get("weeklyHours") != null) weeklyHours = Integer.parseInt(si.get("weeklyHours").toString());
                    }
                } catch (Exception ignored) {}
            }

            Map<String, Object> courseMap = new LinkedHashMap<>();
            courseMap.put("planId", plan.getId());
            courseMap.put("courseId", course.getId());
            courseMap.put("courseName", course.getCourseName());
            courseMap.put("courseCode", course.getCourseCode());
            courseMap.put("description", course.getDescription());
            courseMap.put("coverImage", course.getCoverImage());
            courseMap.put("departmentId", course.getDepartmentId());
            courseMap.put("semesterId", plan.getSemesterId());
            courseMap.put("semester", sem != null ? sem.getSemesterName() : "");
            courseMap.put("classId", plan.getClassId());
            courseMap.put("className", sysClass != null ? sysClass.getClassName() : "");
            courseMap.put("scheduleInfo", plan.getScheduleInfo());
            courseMap.put("auditStatus", plan.getAuditStatus());
            courseMap.put("auditComment", plan.getAuditComment());
            courseMap.put("createTime", plan.getCreateTime());
            courseMap.put("studentCount", studentCount);
            courseMap.put("totalHours", totalHours);
            courseMap.put("weeklyHours", weeklyHours);
            courseMap.put("completedHours", (int) (totalHours * progress.doubleValue() / 100));
            courseMap.put("progress", progress.intValue());

            courseList.add(courseMap);
        }

        Page<Map<String, Object>> resultPage = new Page<>(planPage.getCurrent(), planPage.getSize());
        resultPage.setTotal(planPageResult.getTotal());
        resultPage.setRecords(courseList);

        return resultPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @SuppressWarnings("unchecked")
    public Map<String, Object> createCourse(Map<String, Object> courseData) {
        Long teacherId = UserContext.getUserId();
        String teacherName = UserContext.getUsername();

        // 1. 创建 Course
        Course course = new Course();
        course.setCourseName((String) courseData.get("courseName"));
        course.setCourseCode((String) courseData.get("courseCode"));
        course.setDescription((String) courseData.get("description"));
        course.setCoverImage((String) courseData.get("coverImage"));
        // 获取 departmentId：前端传 > 教师所属院系
        //
        // > 默认第1个院系
        if (courseData.containsKey("departmentId") && courseData.get("departmentId") != null) {
            course.setDepartmentId(Long.valueOf(courseData.get("departmentId").toString()));
        } else {
            SysUser teacher = sysUserMapper.selectById(teacherId);
            Long deptId = (teacher != null) ? teacher.getDepartmentId() : null;
            if (deptId == null) {
                // 兜底：取数据库中第一个院系
                List<SysDepartment> depts = sysDepartmentMapper.selectList(null);
                deptId = (depts != null && !depts.isEmpty()) ? depts.get(0).getId() : 1L;
            }
            course.setDepartmentId(deptId);
        }
        course.setTeacherId(teacherId);
        course.setAuditStatus(AUDIT_PENDING);
        course.setStatus(1);

        courseMapper.insert(course);

        // 2. 创建 CoursePlan
        CoursePlan plan = new CoursePlan();
        plan.setCourseId(course.getId());

        // 兼容前端字段名：semester(学期名称字符串) / semesterId(Long)
        Object semesterVal = readInputField(courseData, "semester", "semesterId");
        if (semesterVal instanceof String) {
            plan.setSemesterId(resolveSemesterId((String) semesterVal));
        } else if (semesterVal != null) {
            plan.setSemesterId(Long.valueOf(semesterVal.toString()));
        }

        // 兼容前端字段名：className(班级名称字符串) / classId(Long)
        Object classVal = readInputField(courseData, "className", "classId");
        if (classVal instanceof String) {
            Long resolved = resolveClassId((String) classVal);
            if (resolved == null) {
                throw new BusinessException(400, "未找到班级：" + classVal);
            }
            plan.setClassId(resolved);
        } else if (classVal != null) {
            plan.setClassId(Long.valueOf(classVal.toString()));
        } else {
            // 兜底：使用教师所属班级（如果教师只有一个班级）
            QueryWrapper<CoursePlan> existingPlanQw = new QueryWrapper<>();
            existingPlanQw.eq("teacher_id", teacherId)
                    .eq("audit_status", AUDIT_APPROVED)
                    .select("class_id")
                    .last("LIMIT 1");
            CoursePlan existingPlan = coursePlanMapper.selectOne(existingPlanQw);
            if (existingPlan != null && existingPlan.getClassId() != null) {
                plan.setClassId(existingPlan.getClassId());
            } else {
                throw new BusinessException(400, "班级信息不能为空，请传入 classId 或 className");
            }
        }

        plan.setTeacherId(teacherId);

        // 如果前端没有传 scheduleInfo，则从 totalHours/weeklyHours/startDate/endDate 构建 JSON
        String scheduleInfo = (String) courseData.get("scheduleInfo");
        if (scheduleInfo == null) {
            try {
                Map<String, Object> si = new LinkedHashMap<>();
                if (courseData.containsKey("totalHours")) si.put("totalHours", courseData.get("totalHours"));
                if (courseData.containsKey("weeklyHours")) si.put("weeklyHours", courseData.get("weeklyHours"));
                if (courseData.containsKey("startDate")) si.put("startDate", courseData.get("startDate"));
                if (courseData.containsKey("endDate")) si.put("endDate", courseData.get("endDate"));
                if (!si.isEmpty()) {
                    scheduleInfo = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(si);
                }
            } catch (Exception ignored) {}
        }
        plan.setScheduleInfo(scheduleInfo);
        plan.setAuditStatus(AUDIT_PENDING);

        coursePlanMapper.insert(plan);

        // 3. 创建章节
        List<Map<String, Object>> chapters = (List<Map<String, Object>>) courseData.get("chapters");
        if (chapters != null && !chapters.isEmpty()) {
            for (int i = 0; i < chapters.size(); i++) {
                Map<String, Object> chData = chapters.get(i);
                CourseChapter chapter = new CourseChapter();
                chapter.setCourseId(course.getId());
                chapter.setChapterName((String) chData.get("chapterName"));
                chapter.setChapterOrder(chData.get("chapterOrder") != null
                        ? Integer.valueOf(chData.get("chapterOrder").toString()) : i + 1);
                chapter.setVideoUrl((String) chData.get("videoUrl"));
                chapter.setVideoDuration(chData.get("videoDuration") != null
                        ? Integer.valueOf(chData.get("videoDuration").toString()) : 0);
                chapter.setContentText((String) chData.get("contentText"));
                chapter.setAttachmentUrl((String) chData.get("attachmentUrl"));
                courseChapterMapper.insert(chapter);
            }
        }

        // 4. 记录审核日志
        SysAuditLog auditLog = new SysAuditLog();
        auditLog.setBizType("COURSE");
        auditLog.setBizId(course.getId());
        auditLog.setBizName(course.getCourseName());
        auditLog.setAction("SUBMIT");
        auditLog.setOperatorId(teacherId);
        auditLog.setOperatorName(teacherName);
        auditLog.setAfterStatus(AUDIT_PENDING);
        sysAuditLogMapper.insert(auditLog);

        log.info("教师 {} 创建了课程 {}（ID: {}）", teacherId, course.getCourseName(), course.getId());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("courseId", course.getId());
        result.put("planId", plan.getId());
        result.put("courseName", course.getCourseName());
        result.put("auditStatus", AUDIT_PENDING);

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @SuppressWarnings("unchecked")
    public void updateCourse(Long courseId, Map<String, Object> courseData) {
        Long teacherId = UserContext.getUserId();

        // 1. 校验课程是否存在
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new BusinessException(404, "课程不存在");
        }

        // 2. 校验审核状态
        String auditStatus = course.getAuditStatus();
        if (!AUDIT_PENDING.equals(auditStatus) && !AUDIT_REJECTED.equals(auditStatus)) {
            throw new BusinessException(400, "当前状态不允许修改，仅待审核或已驳回的课程可修改");
        }

        // 3. 更新课程基本信息
        if (courseData.containsKey("courseName")) {
            course.setCourseName((String) courseData.get("courseName"));
        }
        if (courseData.containsKey("courseCode")) {
            course.setCourseCode((String) courseData.get("courseCode"));
        }
        if (courseData.containsKey("description")) {
            course.setDescription((String) courseData.get("description"));
        }
        if (courseData.containsKey("coverImage")) {
            course.setCoverImage((String) courseData.get("coverImage"));
        }
        if (courseData.containsKey("departmentId")) {
            course.setDepartmentId(Long.valueOf(courseData.get("departmentId").toString()));
        }
        courseMapper.updateById(course);

        // 4. 更新 CoursePlan
        QueryWrapper<CoursePlan> planQw = new QueryWrapper<>();
        planQw.eq("course_id", courseId);
        CoursePlan plan = coursePlanMapper.selectOne(planQw);
        if (plan != null) {
            // 兼容前端字段名：semester(学期名称字符串) / semesterId(Long)
            Object semVal = readInputField(courseData, "semester", "semesterId");
            if (semVal instanceof String) {
                plan.setSemesterId(resolveSemesterId((String) semVal));
            } else if (semVal != null) {
                plan.setSemesterId(Long.valueOf(semVal.toString()));
            }

            // 兼容前端字段名：className(班级名称字符串) / classId(Long)
            Object clsVal = readInputField(courseData, "className", "classId");
            if (clsVal instanceof String) {
                plan.setClassId(resolveClassId((String) clsVal));
            } else if (clsVal != null) {
                plan.setClassId(Long.valueOf(clsVal.toString()));
            }

            if (courseData.containsKey("scheduleInfo")) {
                plan.setScheduleInfo((String) courseData.get("scheduleInfo"));
            }
            coursePlanMapper.updateById(plan);
        }

        // 5. 更新章节：先删除原有章节，再重新插入
        List<Map<String, Object>> chapters = (List<Map<String, Object>>) courseData.get("chapters");
        if (chapters != null) {
            // 删除原有章节
            QueryWrapper<CourseChapter> delChQw = new QueryWrapper<>();
            delChQw.eq("course_id", courseId);
            courseChapterMapper.delete(delChQw);

            // 重新插入
            for (int i = 0; i < chapters.size(); i++) {
                Map<String, Object> chData = chapters.get(i);
                CourseChapter chapter = new CourseChapter();
                chapter.setCourseId(courseId);
                chapter.setChapterName((String) chData.get("chapterName"));
                chapter.setChapterOrder(chData.get("chapterOrder") != null
                        ? Integer.valueOf(chData.get("chapterOrder").toString()) : i + 1);
                chapter.setVideoUrl((String) chData.get("videoUrl"));
                chapter.setVideoDuration(chData.get("videoDuration") != null
                        ? Integer.valueOf(chData.get("videoDuration").toString()) : 0);
                chapter.setContentText((String) chData.get("contentText"));
                chapter.setAttachmentUrl((String) chData.get("attachmentUrl"));
                courseChapterMapper.insert(chapter);
            }
        }

        log.info("教师 {} 更新了课程 {}", teacherId, courseId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCourse(Long courseId) {
        Long teacherId = UserContext.getUserId();

        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new BusinessException(404, "课程不存在");
        }

        String auditStatus = course.getAuditStatus();
        if (!AUDIT_PENDING.equals(auditStatus) && !AUDIT_REJECTED.equals(auditStatus)) {
            throw new BusinessException(400, "当前状态不允许删除，仅待审核或已驳回的课程可删除");
        }

        // 软删除课程（@TableLogic 会自动处理）
        courseMapper.deleteById(courseId);

        // 同时软删除开课计划
        QueryWrapper<CoursePlan> planQw = new QueryWrapper<>();
        planQw.eq("course_id", courseId);
        coursePlanMapper.delete(planQw);

        log.info("教师 {} 删除了课程 {}", teacherId, courseId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateChapters(Long courseId, java.util.List<Map<String, Object>> chapters) {
        Long teacherId = UserContext.getUserId();

        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new BusinessException(404, "课程不存在");
        }

        // 删除原有章节
        QueryWrapper<CourseChapter> delQw = new QueryWrapper<>();
        delQw.eq("course_id", courseId);
        courseChapterMapper.delete(delQw);

        // 插入新章节
        if (chapters != null && !chapters.isEmpty()) {
            for (int i = 0; i < chapters.size(); i++) {
                Map<String, Object> ch = chapters.get(i);
                CourseChapter chapter = new CourseChapter();
                chapter.setCourseId(courseId);
                chapter.setChapterName((String) ch.get("chapterName"));
                chapter.setChapterOrder(ch.get("chapterOrder") != null
                        ? Integer.valueOf(ch.get("chapterOrder").toString()) : i + 1);
                chapter.setVideoUrl((String) ch.get("videoUrl"));
                chapter.setVideoDuration(ch.get("videoDuration") != null
                        ? Integer.valueOf(ch.get("videoDuration").toString()) : 0);
                chapter.setContentText((String) ch.get("contentText"));
                chapter.setAttachmentUrl((String) ch.get("attachmentUrl"));
                courseChapterMapper.insert(chapter);
            }
        }

        log.info("教师 {} 更新了课程 {} 的章节，共 {} 个", teacherId, courseId,
                chapters != null ? chapters.size() : 0);
    }

    @Override
    public Map<String, Object> getCourseProgress(Long courseId) {
        // 1. 查询课程信息
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new BusinessException(404, "课程不存在");
        }

        // 2. 查询所有章节
        QueryWrapper<CourseChapter> chapterQw = new QueryWrapper<>();
        chapterQw.eq("course_id", courseId)
                .orderByAsc("chapter_order");
        List<CourseChapter> chapters = courseChapterMapper.selectList(chapterQw);

        // 3. 查询所有选课学生
        QueryWrapper<CoursePlan> planQw = new QueryWrapper<>();
        planQw.eq("course_id", courseId);
        CoursePlan plan = coursePlanMapper.selectOne(planQw);

        List<Map<String, Object>> studentProgressList = new ArrayList<>();
        List<Map<String, Object>> chapterProgressList = new ArrayList<>();
        int totalStudents = 0;
        BigDecimal overallProgress = BigDecimal.ZERO;

        if (plan != null) {
            QueryWrapper<StudentCourseEnrollment> enrollQw = new QueryWrapper<>();
            enrollQw.eq("course_plan_id", plan.getId());
            List<StudentCourseEnrollment> enrollments = enrollmentMapper.selectList(enrollQw);
            totalStudents = enrollments.size();

            // 统计总体平均进度
            if (!enrollments.isEmpty()) {
                BigDecimal sumProgress = enrollments.stream()
                        .map(e -> e.getProgressPercent() != null ? e.getProgressPercent() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                overallProgress = sumProgress.divide(BigDecimal.valueOf(enrollments.size()), 2, RoundingMode.HALF_UP);
            }

            // 统计每个学生的进度
            for (StudentCourseEnrollment enrollment : enrollments) {
                SysUser student = sysUserMapper.selectById(enrollment.getStudentId());
                if (student == null) continue;

                // 查询该学生最后学习时间
                QueryWrapper<StudentLearningProgress> lastLpQw = new QueryWrapper<>();
                lastLpQw.eq("student_id", enrollment.getStudentId())
                        .eq("course_id", courseId)
                        .orderByDesc("last_watch_time")
                        .last("LIMIT 1");
                StudentLearningProgress lastLp = learningProgressMapper.selectOne(lastLpQw);

                Map<String, Object> sp = new LinkedHashMap<>();
                sp.put("studentId", student.getId());
                sp.put("userName", student.getRealName() != null ? student.getRealName() : student.getUsername());
                sp.put("progress", enrollment.getProgressPercent());
                sp.put("isCompleted", enrollment.getIsCompleted());
                sp.put("lastStudyTime", lastLp != null ? lastLp.getLastWatchTime() : null);
                studentProgressList.add(sp);
            }

            // 统计章节平均进度
            for (CourseChapter chapter : chapters) {
                QueryWrapper<StudentLearningProgress> chLpQw = new QueryWrapper<>();
                chLpQw.eq("course_id", courseId)
                        .eq("chapter_id", chapter.getId())
                        .eq("is_completed", 1);
                long completedCount = learningProgressMapper.selectCount(chLpQw);

                BigDecimal avgChapterProgress = BigDecimal.ZERO;
                if (totalStudents > 0) {
                    avgChapterProgress = BigDecimal.valueOf(completedCount)
                            .multiply(BigDecimal.valueOf(100))
                            .divide(BigDecimal.valueOf(totalStudents), 2, RoundingMode.HALF_UP);
                }

                Map<String, Object> cp = new LinkedHashMap<>();
                cp.put("chapterId", chapter.getId());
                cp.put("chapterName", chapter.getChapterName());
                cp.put("chapterOrder", chapter.getChapterOrder());
                cp.put("videoUrl", chapter.getVideoUrl());
                cp.put("videoDuration", chapter.getVideoDuration());
                cp.put("contentText", chapter.getContentText());
                cp.put("attachmentUrl", chapter.getAttachmentUrl());
                cp.put("completedCount", completedCount);
                cp.put("totalStudents", totalStudents);
                cp.put("avgProgress", avgChapterProgress);
                chapterProgressList.add(cp);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("courseId", courseId);
        result.put("courseName", course.getCourseName());
        result.put("totalStudents", totalStudents);
        result.put("overallProgress", overallProgress);
        result.put("totalChapters", chapters.size());
        result.put("chapterProgress", chapterProgressList);
        result.put("studentProgress", studentProgressList);
        result.put("students", studentProgressList);  // 前端成绩弹窗读取的别名

        return result;
    }
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int saveGrades(Long courseId, List<Map<String, Object>> grades) {
        Long teacherId = UserContext.getUserId();

        // 获取 CoursePlan
        QueryWrapper<CoursePlan> planQw = new QueryWrapper<>();
        planQw.eq("course_id", courseId);
        CoursePlan plan = coursePlanMapper.selectOne(planQw);
        if (plan == null) {
            throw new BusinessException(404, "开课计划不存在");
        }

        int updatedCount = 0;

        for (Map<String, Object> gradeEntry : grades) {
            Object studentIdObj = gradeEntry.get("studentId");
            if (studentIdObj == null) {
                throw new BusinessException(400, "studentId 不能为空");
            }
            Long studentId = Long.valueOf(studentIdObj.toString());

            // 查找现有成绩
            QueryWrapper<StudentGrade> existQw = new QueryWrapper<>();
            existQw.eq("student_id", studentId)
                    .eq("course_plan_id", plan.getId());
            StudentGrade grade = studentGradeMapper.selectOne(existQw);

            boolean isNew = false;
            if (grade == null) {
                grade = new StudentGrade();
                grade.setStudentId(studentId);
                grade.setCoursePlanId(plan.getId());
                if (gradeEntry.containsKey("semesterId")) {
                    grade.setSemesterId(Long.valueOf(gradeEntry.get("semesterId").toString()));
                } else {
                    grade.setSemesterId(plan.getSemesterId());
                }
                isNew = true;
            }

            // 设置各分项成绩
            if (gradeEntry.containsKey("usualGrade")) {
                grade.setUsualGrade(new BigDecimal(gradeEntry.get("usualGrade").toString()));
            }
            if (gradeEntry.containsKey("examGrade")) {
                grade.setExamGrade(new BigDecimal(gradeEntry.get("examGrade").toString()));
            }
            // 实验/实训成绩：允许手动覆盖，传 null 则不写入（保持自动计算值）
            if (gradeEntry.get("experimentGrade") != null) {
                grade.setExperimentGrade(new BigDecimal(gradeEntry.get("experimentGrade").toString()));
            }
            if (gradeEntry.get("trainingGrade") != null) {
                grade.setTrainingGrade(new BigDecimal(gradeEntry.get("trainingGrade").toString()));
            }

            // 计算最终成绩：平时*0.3 + 考试*0.4 + 实验*0.3
            if (grade.getUsualGrade() != null && grade.getExamGrade() != null && grade.getExperimentGrade() != null) {
                BigDecimal finalGrade = grade.getUsualGrade().multiply(BigDecimal.valueOf(0.3))
                        .add(grade.getExamGrade().multiply(BigDecimal.valueOf(0.4)))
                        .add(grade.getExperimentGrade().multiply(BigDecimal.valueOf(0.3)));
                grade.setFinalGrade(finalGrade.setScale(2, RoundingMode.HALF_UP));
            }

            // 设置成绩评语
            if (gradeEntry.containsKey("gradeComment")) {
                grade.setGradeComment((String) gradeEntry.get("gradeComment"));
            }

            grade.setIsPublished(1);

            if (isNew) {
                studentGradeMapper.insert(grade);
            } else {
                studentGradeMapper.updateById(grade);
            }
            updatedCount++;
        }

        log.info("教师 {} 为课程 {} 录入了 {} 条成绩", teacherId, courseId, updatedCount);
        return updatedCount;
    }

    @Override
    public List<Map<String, Object>> getGrades(Long courseId) {
        // 1. 查找课程计划
        QueryWrapper<CoursePlan> planQw = new QueryWrapper<>();
        planQw.eq("course_id", courseId);
        CoursePlan plan = coursePlanMapper.selectOne(planQw);
        if (plan == null) {
            return new ArrayList<>();
        }

        // 2. 查询所有选课学生
        QueryWrapper<StudentCourseEnrollment> enrollQw = new QueryWrapper<>();
        enrollQw.eq("course_plan_id", plan.getId());
        List<StudentCourseEnrollment> enrollments = enrollmentMapper.selectList(enrollQw);

        // 3. 查询该课程的所有实验/实训任务
        QueryWrapper<ExperimentTask> courseTaskQw = new QueryWrapper<>();
        courseTaskQw.eq("course_id", courseId);
        List<ExperimentTask> courseTasks = experimentTaskMapper.selectList(courseTaskQw);
        Map<Long, String> taskTypeMap = new LinkedHashMap<>(); // taskId -> EXPERIMENT/TRAINING
        for (ExperimentTask t : courseTasks) {
            ExperimentProject proj = experimentProjectMapper.selectById(t.getProjectId());
            taskTypeMap.put(t.getId(), proj != null ? proj.getProjectType() : "EXPERIMENT");
        }

        // 4. 为每个学生查成绩
        List<Map<String, Object>> result = new ArrayList<>();
        for (StudentCourseEnrollment enrollment : enrollments) {
            SysUser student = sysUserMapper.selectById(enrollment.getStudentId());
            if (student == null) continue;

            QueryWrapper<StudentGrade> gradeQw = new QueryWrapper<>();
            gradeQw.eq("student_id", enrollment.getStudentId())
                    .eq("course_plan_id", plan.getId());
            StudentGrade grade = studentGradeMapper.selectOne(gradeQw);

            // 自动计算实验/实训平均分
            BigDecimal autoExperimentAvg = calcAvgTaskScore(enrollment.getStudentId(), courseTasks, taskTypeMap, "EXPERIMENT");
            BigDecimal autoTrainingAvg = calcAvgTaskScore(enrollment.getStudentId(), courseTasks, taskTypeMap, "TRAINING");

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("studentId", student.getId());
            item.put("userName", student.getRealName() != null ? student.getRealName() : student.getUsername());
            item.put("usualGrade", grade != null ? grade.getUsualGrade() : null);
            item.put("examGrade", grade != null ? grade.getExamGrade() : null);
            // 实验/实训优先取已保存的手动值，否则用自动计算值
            item.put("experimentGrade", grade != null && grade.getExperimentGrade() != null
                    ? grade.getExperimentGrade() : autoExperimentAvg);
            item.put("trainingGrade", grade != null && grade.getTrainingGrade() != null
                    ? grade.getTrainingGrade() : autoTrainingAvg);
            item.put("finalGrade", grade != null ? grade.getFinalGrade() : null);
            result.add(item);
        }

        return result;
    }

    // ==================== 实验任务管理 ====================

    @Override
    public IPage<Map<String, Object>> getTasks(String taskType, Long courseId, String auditStatus,
                                                Integer page, Integer pageSize) {
        Long teacherId = UserContext.getUserId();

        Page<ExperimentTask> taskPage = new Page<>(page, pageSize);
        QueryWrapper<ExperimentTask> taskQw = new QueryWrapper<>();
        taskQw.eq("teacher_id", teacherId);

        if (auditStatus != null && !auditStatus.isEmpty()) {
            taskQw.eq("audit_status", auditStatus);
        }
        taskQw.orderByDesc("create_time");

        IPage<ExperimentTask> taskPageResult = experimentTaskMapper.selectPage(taskPage, taskQw);
        List<ExperimentTask> tasks = taskPageResult.getRecords();

        List<Map<String, Object>> taskList = new ArrayList<>();
        for (ExperimentTask task : tasks) {
            ExperimentProject project = experimentProjectMapper.selectById(task.getProjectId());

            // 按项目类型过滤
            if (taskType != null && !taskType.isEmpty()) {
                if (project == null || !taskType.equals(project.getProjectType())) {
                    continue;
                }
            }

            SysClass sysClass = task.getClassId() != null ? sysClassMapper.selectById(task.getClassId()) : null;

            // 直接通过 task.courseId 获取课程名称
            String courseName = "";
            if (task.getCourseId() != null) {
                Course course = courseMapper.selectById(task.getCourseId());
                courseName = course != null ? course.getCourseName() : "";
            }

            // 查询学生数和提交统计
            long studentCount = 0;
            long submittedCount = 0;
            long gradedCount = 0;
            if (task.getClassId() != null) {
                QueryWrapper<SysUser> stuCountQw = new QueryWrapper<>();
                stuCountQw.eq("class_id", task.getClassId());
                studentCount = sysUserMapper.selectCount(stuCountQw);

                QueryWrapper<StudentExperimentSubmission> subCountQw = new QueryWrapper<>();
                subCountQw.eq("task_id", task.getId());
                List<StudentExperimentSubmission> allSubs = submissionMapper.selectList(subCountQw);
                for (StudentExperimentSubmission s : allSubs) {
                    if (SUBMIT_SUBMITTED.equals(s.getStatus())) submittedCount++;
                    if (SUBMIT_GRADED.equals(s.getStatus())) gradedCount++;
                }
            }

            Map<String, Object> taskMap = new LinkedHashMap<>();
            taskMap.put("taskId", task.getId());
            taskMap.put("projectId", task.getProjectId());
            taskMap.put("title", project != null ? project.getProjectName() : "");
            taskMap.put("taskType", project != null ? project.getProjectType() : "");
            taskMap.put("description", project != null ? project.getDescription() : "");
            taskMap.put("courseName", courseName);
            taskMap.put("classId", task.getClassId());
            taskMap.put("className", sysClass != null ? sysClass.getClassName() : "");
            taskMap.put("startTime", task.getStartTime());
            taskMap.put("endTime", task.getEndTime());
            taskMap.put("status", task.getStatus());
            taskMap.put("auditStatus", task.getAuditStatus());
            taskMap.put("studentCount", studentCount);
            taskMap.put("guideFileUrl", project != null ? project.getGuideFileUrl() : "");
            taskMap.put("submittedCount", submittedCount);
            taskMap.put("gradedCount", gradedCount);

            taskList.add(taskMap);
        }

        Page<Map<String, Object>> resultPage = new Page<>(taskPage.getCurrent(), taskPage.getSize());
        resultPage.setTotal(taskPageResult.getTotal());
        resultPage.setRecords(taskList);

        return resultPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createTask(Map<String, Object> taskData) {
        Long teacherId = UserContext.getUserId();
        String teacherName = UserContext.getUsername();

        // 1. 创建实验项目
        ExperimentProject project = new ExperimentProject();
        // 兼容前端字段名：title→projectName, taskType→projectType
        String pName = (String) readInputField(taskData, "title", "projectName");
        project.setProjectName(pName != null ? pName : (String) taskData.get("projectName"));
        project.setDescription((String) taskData.get("description"));
        String pType = (String) readInputField(taskData, "taskType", "projectType");
        project.setProjectType(pType != null ? pType : (String) taskData.get("projectType"));
        project.setTeacherId(teacherId);
        project.setGuideFileUrl((String) taskData.get("guideFileUrl"));
        project.setAuditStatus(AUDIT_PENDING);

        experimentProjectMapper.insert(project);

        // 2. 创建实验任务
        ExperimentTask task = new ExperimentTask();
        task.setProjectId(project.getId());
        // 兼容前端字段名：className(班级名称字符串) / classId(Long)
        Object clsVal = readInputField(taskData, "className", "classId");
        if (clsVal instanceof String) {
            task.setClassId(resolveClassId((String) clsVal));
        } else if (clsVal != null) {
            task.setClassId(Long.valueOf(clsVal.toString()));
        }
        // 关联课程：优先用 courseId，其次用 courseName 模糊匹配
        Object courseVal = readInputField(taskData, "courseName", "courseId");
        if (courseVal instanceof String) {
            Long resolved = resolveCourseId((String) courseVal);
            if (resolved != null) task.setCourseId(resolved);
        } else if (courseVal != null) {
            task.setCourseId(Long.valueOf(courseVal.toString()));
        }
        task.setTeacherId(teacherId);
        task.setStartTime(taskData.get("startTime") != null
                ? LocalDateTime.parse(taskData.get("startTime").toString().replace(" ", "T").replaceFirst("\\.[0-9]+Z?$", "").replaceFirst("[+-]\\d{2}:\\d{2}$", "")) : LocalDateTime.now());
        task.setEndTime(taskData.get("endTime") != null
                ? LocalDateTime.parse(taskData.get("endTime").toString().replace(" ", "T").replaceFirst("\\.[0-9]+Z?$", "").replaceFirst("[+-]\\d{2}:\\d{2}$", "")) : null);
        task.setAuditStatus(AUDIT_PENDING);
        task.setStatus("ACTIVE");

        experimentTaskMapper.insert(task);

        // 3. 记录审核日志
        SysAuditLog auditLog = new SysAuditLog();
        auditLog.setBizType("TASK");
        auditLog.setBizId(task.getId());
        auditLog.setBizName(project.getProjectName());
        auditLog.setAction("SUBMIT");
        auditLog.setOperatorId(teacherId);
        auditLog.setOperatorName(teacherName);
        auditLog.setAfterStatus(AUDIT_PENDING);
        sysAuditLogMapper.insert(auditLog);

        log.info("教师 {} 创建了实验任务 {}（项目: {}）", teacherId, task.getId(), project.getProjectName());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", task.getId());
        result.put("projectId", project.getId());
        result.put("projectName", project.getProjectName());
        result.put("auditStatus", AUDIT_PENDING);

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTask(Long taskId, Map<String, Object> taskData) {
        Long teacherId = UserContext.getUserId();

        ExperimentTask task = experimentTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(404, "实验任务不存在");
        }

        String auditStatus = task.getAuditStatus();
        if (!AUDIT_PENDING.equals(auditStatus) && !AUDIT_REJECTED.equals(auditStatus)) {
            throw new BusinessException(400, "当前状态不允许修改");
        }

        // 更新任务信息
        // 兼容前端字段名：className(班级名称字符串) / classId(Long)
        Object clsVal = readInputField(taskData, "className", "classId");
        if (clsVal instanceof String) {
            task.setClassId(resolveClassId((String) clsVal));
        } else if (clsVal != null) {
            task.setClassId(Long.valueOf(clsVal.toString()));
        }
        if (taskData.containsKey("startTime")) {
            task.setStartTime(LocalDateTime.parse(taskData.get("startTime").toString().replace(" ", "T").replaceFirst("\\.[0-9]+Z?$", "").replaceFirst("[+-]\\d{2}:\\d{2}$", "")));
        }
        if (taskData.containsKey("endTime")) {
            task.setEndTime(LocalDateTime.parse(taskData.get("endTime").toString().replace(" ", "T").replaceFirst("\\.[0-9]+Z?$", "").replaceFirst("[+-]\\d{2}:\\d{2}$", "")));
        }
        if (taskData.containsKey("status")) {
            task.setStatus((String) taskData.get("status"));
        }
        experimentTaskMapper.updateById(task);

        // 更新项目信息
        if (task.getProjectId() != null) {
            ExperimentProject project = experimentProjectMapper.selectById(task.getProjectId());
            if (project != null) {
                // 兼容前端字段名：title→projectName, taskType→projectType
                String pName = (String) readInputField(taskData, "title", "projectName");
                if (pName != null) {
                    project.setProjectName(pName);
                } else if (taskData.containsKey("projectName")) {
                    project.setProjectName((String) taskData.get("projectName"));
                }
                if (taskData.containsKey("description")) {
                    project.setDescription((String) taskData.get("description"));
                }
                String pType = (String) readInputField(taskData, "taskType", "projectType");
                if (pType != null) {
                    project.setProjectType(pType);
                } else if (taskData.containsKey("projectType")) {
                    project.setProjectType((String) taskData.get("projectType"));
                }
                if (taskData.containsKey("guideFileUrl")) {
                    project.setGuideFileUrl((String) taskData.get("guideFileUrl"));
                }
                experimentProjectMapper.updateById(project);
            }
        }

        log.info("教师 {} 更新了实验任务 {}", teacherId, taskId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTask(Long taskId) {
        Long teacherId = UserContext.getUserId();

        ExperimentTask task = experimentTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(404, "实验任务不存在");
        }

        String auditStatus = task.getAuditStatus();
        if (!AUDIT_PENDING.equals(auditStatus) && !AUDIT_REJECTED.equals(auditStatus)) {
            throw new BusinessException(400, "当前状态不允许删除");
        }

        // 软删除任务
        experimentTaskMapper.deleteById(taskId);
        // 同时软删除关联的项目
        if (task.getProjectId() != null) {
            experimentProjectMapper.deleteById(task.getProjectId());
        }

        log.info("教师 {} 删除了实验任务 {}", teacherId, taskId);
    }

    @Override
    public Map<String, Object> getTaskSubmissions(Long taskId, String status, Integer page, Integer pageSize) {
        // 1. 查询任务
        ExperimentTask task = experimentTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(404, "实验任务不存在");
        }

        // 2. 查询班级总学生数
        long totalStudents = 0;
        if (task.getClassId() != null) {
            QueryWrapper<SysUser> stuQw = new QueryWrapper<>();
            stuQw.eq("class_id", task.getClassId());
            totalStudents = sysUserMapper.selectCount(stuQw);
        }

        // 3. 分页查询提交记录
        Page<StudentExperimentSubmission> subPage = new Page<>(page, pageSize);
        QueryWrapper<StudentExperimentSubmission> subQw = new QueryWrapper<>();
        subQw.eq("task_id", taskId);

        if (status != null && !status.isEmpty()) {
            subQw.eq("status", status);
        }
        subQw.orderByDesc("submit_time");

        IPage<StudentExperimentSubmission> subPageResult = submissionMapper.selectPage(subPage, subQw);
        List<StudentExperimentSubmission> submissions = subPageResult.getRecords();

        // 4. 组装数据
        int submittedCount = 0;
        int gradedCount = 0;

        List<Map<String, Object>> submissionList = new ArrayList<>();
        for (StudentExperimentSubmission sub : submissions) {
            SysUser student = sysUserMapper.selectById(sub.getStudentId());

            if (SUBMIT_SUBMITTED.equals(sub.getStatus())) {
                submittedCount++;
            } else if (SUBMIT_GRADED.equals(sub.getStatus())) {
                gradedCount++;
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("submissionId", sub.getId());
            item.put("studentId", sub.getStudentId());
            item.put("userName", student != null ? (student.getRealName() != null ? student.getRealName() : student.getUsername()) : "");
            item.put("processDescription", sub.getProcessDescription());
            item.put("reportFileUrl", sub.getReportFileUrl());
            item.put("reportFileName", sub.getReportFileName());
            item.put("score", sub.getScore());
            item.put("comment", sub.getTeacherComment());
            item.put("status", sub.getStatus());
            item.put("submitTime", sub.getSubmitTime());
            item.put("gradeTime", sub.getGradeTime());
            item.put("resubmitCount", sub.getResubmitCount());

            submissionList.add(item);
        }

        // 5. 统计汇总
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalStudents", totalStudents);
        summary.put("submittedCount", submittedCount);
        summary.put("gradedCount", gradedCount);
        summary.put("unsubmittedCount", totalStudents - submissionList.size());

        // 6. 组装结果
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", summary);
        result.put("records", submissionList);
        result.put("total", subPageResult.getTotal());
        result.put("page", subPageResult.getCurrent());
        result.put("pageSize", subPageResult.getSize());

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> gradeSubmission(Long submissionId, Integer score, String comment, String action) {
        Long teacherId = UserContext.getUserId();

        StudentExperimentSubmission submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            throw new BusinessException(404, "提交记录不存在");
        }

        // 设置成绩和评语
        submission.setScore(score != null ? BigDecimal.valueOf(score) : null);
        submission.setTeacherComment(comment);
        submission.setGradeTime(LocalDateTime.now());

        if ("PASS".equalsIgnoreCase(action)) {
            submission.setStatus(SUBMIT_GRADED);
        } else if ("RETURN".equalsIgnoreCase(action)) {
            submission.setStatus(SUBMIT_RETURNED);
        } else {
            throw new BusinessException(400, "无效的操作类型：" + action);
        }

        submissionMapper.updateById(submission);

        // 发送消息通知学生
        SysMessage msg = new SysMessage();
        msg.setSenderId(teacherId);
        msg.setReceiverId(submission.getStudentId());
        msg.setTitle("实验任务批阅结果");
        msg.setContent(SUBMIT_GRADED.equals(submission.getStatus())
                ? "您的实验提交已批阅，成绩：" + score + "分。评语：" + (comment != null ? comment : "无")
                : "您的实验提交已被退回，请查看评语后重新提交。评语：" + (comment != null ? comment : "无"));
        msg.setMessageType("GRADE");
        msg.setRelatedBizType("TASK");
        msg.setRelatedBizId(submission.getTaskId());
        sysMessageMapper.insert(msg);

        log.info("教师 {} 批阅了提交 {}，操作：{}", teacherId, submissionId, action);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("submissionId", submission.getId());
        result.put("score", submission.getScore());
        result.put("status", submission.getStatus());
        result.put("gradeTime", submission.getGradeTime());

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> returnSubmission(Long submissionId, String returnReason) {
        Long teacherId = UserContext.getUserId();

        StudentExperimentSubmission submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            throw new BusinessException(404, "提交记录不存在");
        }

        submission.setStatus(SUBMIT_RETURNED);
        submission.setTeacherComment(returnReason);
        submission.setResubmitCount(submission.getResubmitCount() != null
                ? submission.getResubmitCount() + 1 : 1);
        submissionMapper.updateById(submission);

        // 发送消息通知学生
        SysMessage msg = new SysMessage();
        msg.setSenderId(teacherId);
        msg.setReceiverId(submission.getStudentId());
        msg.setTitle("实验任务被退回");
        msg.setContent("您的实验提交已被退回，退回原因：" + (returnReason != null ? returnReason : "无"));
        msg.setMessageType("GRADE");
        msg.setRelatedBizType("TASK");
        msg.setRelatedBizId(submission.getTaskId());
        sysMessageMapper.insert(msg);

        log.info("教师 {} 退回了提交 {}，原因：{}", teacherId, submissionId, returnReason);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("submissionId", submission.getId());
        result.put("status", submission.getStatus());
        result.put("resubmitCount", submission.getResubmitCount());

        return result;
    }

    // ==================== 教学资源管理 ====================

    @Override
    public IPage<Map<String, Object>> getResources(String auditStatus, String type, String keyword,
                                                    Integer page, Integer pageSize) {
        Long teacherId = UserContext.getUserId();

        Page<TeachingResource> resPage = new Page<>(page, pageSize);
        QueryWrapper<TeachingResource> resQw = new QueryWrapper<>();
        resQw.eq("teacher_id", teacherId);

        if (auditStatus != null && !auditStatus.isEmpty()) {
            resQw.eq("audit_status", auditStatus);
        }
        if (keyword != null && !keyword.isEmpty()) {
            resQw.like("resource_name", keyword);
        }
        if (type != null && !type.isEmpty()) {
            resQw.eq("file_type", type);
        }
        resQw.orderByDesc("create_time");

        IPage<TeachingResource> resPageResult = teachingResourceMapper.selectPage(resPage, resQw);
        List<TeachingResource> resources = resPageResult.getRecords();

        List<Map<String, Object>> resourceList = new ArrayList<>();
        for (TeachingResource res : resources) {
            ResourceCategory category = res.getCategoryId() != null
                    ? resourceCategoryMapper.selectById(res.getCategoryId()) : null;

            Map<String, Object> resMap = new LinkedHashMap<>();
            resMap.put("resourceId", res.getId());
            resMap.put("resourceName", res.getResourceName());
            resMap.put("description", res.getDescription());
            resMap.put("categoryId", res.getCategoryId());
            resMap.put("categoryName", category != null ? category.getCategoryName() : "");
            resMap.put("fileUrl", res.getFileUrl());
            resMap.put("fileName", res.getFileName());
            resMap.put("fileType", res.getFileType());
            resMap.put("fileSize", res.getFileSize());
            resMap.put("visibility", res.getVisibility());
            resMap.put("courseId", res.getCourseId());
            // 根据 courseId 查询课程名称
            if (res.getCourseId() != null) {
                Course c = courseMapper.selectById(res.getCourseId());
                resMap.put("courseName", c != null ? c.getCourseName() : "");
            } else {
                resMap.put("courseName", "");
            }
            // 章节关联信息
            resMap.put("chapterId", res.getChapterId());
            if (res.getChapterId() != null) {
                CourseChapter ch = courseChapterMapper.selectById(res.getChapterId());
                resMap.put("chapterName", ch != null ? ch.getChapterName() : "");
            } else {
                resMap.put("chapterName", "");
            }
            resMap.put("viewCount", res.getViewCount());
            resMap.put("downloadCount", res.getDownloadCount());
            resMap.put("auditStatus", res.getAuditStatus());
            resMap.put("auditComment", res.getAuditComment());
            resMap.put("createTime", res.getCreateTime());
            resMap.put("uploadTime", res.getCreateTime());  // 前端使用的别名

            resourceList.add(resMap);
        }

        Page<Map<String, Object>> resultPage = new Page<>(resPage.getCurrent(), resPage.getSize());
        resultPage.setTotal(resPageResult.getTotal());
        resultPage.setRecords(resourceList);

        return resultPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> uploadResource(Map<String, Object> resourceData) {
        Long teacherId = UserContext.getUserId();
        String teacherName = UserContext.getUsername();

        TeachingResource resource = new TeachingResource();
        resource.setResourceName((String) resourceData.get("resourceName"));
        resource.setDescription((String) resourceData.get("description"));
        if (resourceData.containsKey("categoryId") && resourceData.get("categoryId") != null) {
            resource.setCategoryId(Long.valueOf(resourceData.get("categoryId").toString()));
        } else {
            resource.setCategoryId(1L);
        }
        // 默认值处理：前端可能不传文件信息
        String fileUrl = (String) resourceData.get("fileUrl");
        resource.setFileUrl(fileUrl != null ? fileUrl : "");
        String fileName = (String) resourceData.get("fileName");
        resource.setFileName(fileName != null ? fileName : "");
        resource.setFileType((String) resourceData.get("fileType"));
        if (resourceData.containsKey("fileSize")) {
            resource.setFileSize(Long.valueOf(resourceData.get("fileSize").toString()));
        } else {
            resource.setFileSize(0L);
        }
        resource.setTeacherId(teacherId);
        resource.setVisibility((String) resourceData.get("visibility"));
        // 兼容前端字段名：courseName(课程名称字符串) / courseId(Long)
        if (resourceData.containsKey("courseName")) {
            Long cid = resolveCourseId((String) resourceData.get("courseName"));
            if (cid != null) resource.setCourseId(cid);
        } else if (resourceData.containsKey("courseId")) {
            resource.setCourseId(Long.valueOf(resourceData.get("courseId").toString()));
        }
        // 关联章节
        if (resourceData.containsKey("chapterId") && resourceData.get("chapterId") != null) {
            resource.setChapterId(Long.valueOf(resourceData.get("chapterId").toString()));
        }
        resource.setAuditStatus(AUDIT_PENDING);
        resource.setViewCount(0);
        resource.setDownloadCount(0);

        teachingResourceMapper.insert(resource);

        // 如果是视频类型且关联了章节，同步更新 course_chapter.video_url
        if ("VIDEO".equals(resource.getFileType()) && resource.getChapterId() != null
                && resource.getFileUrl() != null && !resource.getFileUrl().isEmpty()) {
            CourseChapter chapter = courseChapterMapper.selectById(resource.getChapterId());
            if (chapter != null) {
                chapter.setVideoUrl(resource.getFileUrl());
                courseChapterMapper.updateById(chapter);
                log.info("同步章节 {} 的视频 URL: {}", chapter.getId(), resource.getFileUrl());
            }
        }

        teachingResourceMapper.insert(resource);

        // 记录审核日志
        SysAuditLog auditLog = new SysAuditLog();
        auditLog.setBizType("RESOURCE");
        auditLog.setBizId(resource.getId());
        auditLog.setBizName(resource.getResourceName());
        auditLog.setAction("SUBMIT");
        auditLog.setOperatorId(teacherId);
        auditLog.setOperatorName(teacherName);
        auditLog.setAfterStatus(AUDIT_PENDING);
        sysAuditLogMapper.insert(auditLog);

        log.info("教师 {} 上传了教学资源 {}", teacherId, resource.getResourceName());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("resourceId", resource.getId());
        result.put("resourceName", resource.getResourceName());
        result.put("auditStatus", AUDIT_PENDING);

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateResource(Long resourceId, Map<String, Object> resourceData) {
        Long teacherId = UserContext.getUserId();

        TeachingResource resource = teachingResourceMapper.selectById(resourceId);
        if (resource == null) {
            throw new BusinessException(404, "教学资源不存在");
        }

        String auditStatus = resource.getAuditStatus();
        if (!AUDIT_PENDING.equals(auditStatus) && !AUDIT_REJECTED.equals(auditStatus)) {
            throw new BusinessException(400, "当前状态不允许修改");
        }

        if (resourceData.containsKey("resourceName")) {
            resource.setResourceName((String) resourceData.get("resourceName"));
        }
        if (resourceData.containsKey("description")) {
            resource.setDescription((String) resourceData.get("description"));
        }
        if (resourceData.containsKey("categoryId")) {
            resource.setCategoryId(Long.valueOf(resourceData.get("categoryId").toString()));
        }
        if (resourceData.containsKey("fileUrl")) {
            resource.setFileUrl((String) resourceData.get("fileUrl"));
        }
        if (resourceData.containsKey("fileName")) {
            resource.setFileName((String) resourceData.get("fileName"));
        }
        if (resourceData.containsKey("fileType")) {
            resource.setFileType((String) resourceData.get("fileType"));
        }
        if (resourceData.containsKey("fileSize")) {
            resource.setFileSize(Long.valueOf(resourceData.get("fileSize").toString()));
        }
        if (resourceData.containsKey("visibility")) {
            resource.setVisibility((String) resourceData.get("visibility"));
        }
        if (resourceData.containsKey("courseId")) {
            resource.setCourseId(Long.valueOf(resourceData.get("courseId").toString()));
        }
        if (resourceData.containsKey("chapterId")) {
            resource.setChapterId(resourceData.get("chapterId") != null
                    ? Long.valueOf(resourceData.get("chapterId").toString()) : null);
        }

        teachingResourceMapper.updateById(resource);

        // 如果是视频类型且关联了章节，同步更新 course_chapter.video_url
        if ("VIDEO".equals(resource.getFileType()) && resource.getChapterId() != null
                && resource.getFileUrl() != null && !resource.getFileUrl().isEmpty()) {
            CourseChapter chapter = courseChapterMapper.selectById(resource.getChapterId());
            if (chapter != null) {
                chapter.setVideoUrl(resource.getFileUrl());
                courseChapterMapper.updateById(chapter);
                log.info("同步章节 {} 的视频 URL: {}", chapter.getId(), resource.getFileUrl());
            }
        }

        log.info("教师 {} 更新了教学资源 {}", teacherId, resourceId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteResource(Long resourceId) {
        Long teacherId = UserContext.getUserId();

        TeachingResource resource = teachingResourceMapper.selectById(resourceId);
        if (resource == null) {
            throw new BusinessException(404, "教学资源不存在");
        }

        String auditStatus = resource.getAuditStatus();
        if (!AUDIT_PENDING.equals(auditStatus) && !AUDIT_REJECTED.equals(auditStatus)) {
            throw new BusinessException(400, "当前状态不允许删除");
        }

        teachingResourceMapper.deleteById(resourceId);
        log.info("教师 {} 删除了教学资源 {}", teacherId, resourceId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resubmitResource(Long resourceId, Map<String, Object> resourceData) {
        Long teacherId = UserContext.getUserId();

        TeachingResource resource = teachingResourceMapper.selectById(resourceId);
        if (resource == null) {
            throw new BusinessException(404, "教学资源不存在");
        }

        if (!AUDIT_REJECTED.equals(resource.getAuditStatus())) {
            throw new BusinessException(400, "仅已驳回的资源可重新提交审核");
        }

        // 更新资源信息
        if (resourceData.containsKey("resourceName")) {
            resource.setResourceName((String) resourceData.get("resourceName"));
        }
        if (resourceData.containsKey("description")) {
            resource.setDescription((String) resourceData.get("description"));
        }
        if (resourceData.containsKey("fileUrl")) {
            resource.setFileUrl((String) resourceData.get("fileUrl"));
        }
        if (resourceData.containsKey("fileName")) {
            resource.setFileName((String) resourceData.get("fileName"));
        }
        if (resourceData.containsKey("chapterId")) {
            resource.setChapterId(resourceData.get("chapterId") != null
                    ? Long.valueOf(resourceData.get("chapterId").toString()) : null);
        }

        // 重置审核状态为待审核
        resource.setAuditStatus(AUDIT_PENDING);
        resource.setAuditComment(null);
        resource.setAuditAdminId(null);
        resource.setAuditTime(null);

        teachingResourceMapper.updateById(resource);

        // 记录审核日志
        SysAuditLog auditLog = new SysAuditLog();
        auditLog.setBizType("RESOURCE");
        auditLog.setBizId(resource.getId());
        auditLog.setBizName(resource.getResourceName());
        auditLog.setAction("RESUBMIT");
        auditLog.setOperatorId(teacherId);
        auditLog.setOperatorName(UserContext.getUsername());
        auditLog.setBeforeStatus(AUDIT_REJECTED);
        auditLog.setAfterStatus(AUDIT_PENDING);
        sysAuditLogMapper.insert(auditLog);

        log.info("教师 {} 重新提交了教学资源 {} 的审核", teacherId, resourceId);
    }

    // ==================== 学情分析 ====================

    @Override
    public Map<String, Object> getClassOverview(Long classId, Long semester) {
        Long teacherId = UserContext.getUserId();

        // 1. 查询该教师教授的课程计划
        QueryWrapper<CoursePlan> planQw = new QueryWrapper<>();
        planQw.eq("teacher_id", teacherId)
                .eq("audit_status", AUDIT_APPROVED);
        if (classId != null) {
            planQw.eq("class_id", classId);
        }
        if (semester != null) {
            planQw.eq("semester_id", semester);
        }
        List<CoursePlan> myPlans = coursePlanMapper.selectList(planQw);

        if (myPlans.isEmpty()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("totalStudents", 0);
            empty.put("avgCompletionRate", BigDecimal.ZERO);
            empty.put("avgScore", BigDecimal.ZERO);
            empty.put("levelDistribution", new LinkedHashMap<>());
            empty.put("courseRanking", new ArrayList<>());
            return empty;
        }

        // 2. 统计班级人数
        Set<Long> classIdSet = myPlans.stream()
                .map(CoursePlan::getClassId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        long totalStudents = 0;
        if (!classIdSet.isEmpty()) {
            QueryWrapper<SysUser> stuQw = new QueryWrapper<>();
            stuQw.in("class_id", classIdSet);
            totalStudents = sysUserMapper.selectCount(stuQw);
        }

        // 3. 统计平均完成率
        List<StudentCourseEnrollment> allEnrollments = new ArrayList<>();
        for (CoursePlan plan : myPlans) {
            QueryWrapper<StudentCourseEnrollment> enrollQw = new QueryWrapper<>();
            enrollQw.eq("course_plan_id", plan.getId());
            allEnrollments.addAll(enrollmentMapper.selectList(enrollQw));
        }

        BigDecimal avgCompletionRate = BigDecimal.ZERO;
        if (!allEnrollments.isEmpty()) {
            BigDecimal sumProgress = allEnrollments.stream()
                    .map(e -> e.getProgressPercent() != null ? e.getProgressPercent() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            avgCompletionRate = sumProgress.divide(BigDecimal.valueOf(allEnrollments.size()), 2, RoundingMode.HALF_UP);
        }

        // 4. 统计平均分和等级分布
        int excellent = 0;    // >= 90
        int good = 0;         // >= 80
        int needImprove = 0;  // < 80
        BigDecimal totalScore = BigDecimal.ZERO;
        int scoreCount = 0;

        List<Map<String, Object>> courseRanking = new ArrayList<>();

        for (CoursePlan plan : myPlans) {
            QueryWrapper<StudentGrade> gradeQw = new QueryWrapper<>();
            gradeQw.eq("course_plan_id", plan.getId())
                    .eq("is_published", 1);
            List<StudentGrade> grades = studentGradeMapper.selectList(gradeQw);

            Course course = courseMapper.selectById(plan.getCourseId());
            String courseName = course != null ? course.getCourseName() : "";

            BigDecimal courseTotal = BigDecimal.ZERO;
            int courseCount = 0;

            for (StudentGrade g : grades) {
                if (g.getFinalGrade() != null) {
                    totalScore = totalScore.add(g.getFinalGrade());
                    scoreCount++;
                    courseTotal = courseTotal.add(g.getFinalGrade());
                    courseCount++;

                    double val = g.getFinalGrade().doubleValue();
                    if (val >= 90) {
                        excellent++;
                    } else if (val >= 80) {
                        good++;
                    } else {
                        needImprove++;
                    }
                }
            }

            // 课程排名
            if (courseCount > 0) {
                Map<String, Object> cr = new LinkedHashMap<>();
                cr.put("courseId", plan.getCourseId());
                cr.put("courseName", courseName);
                cr.put("avgScore", courseTotal.divide(BigDecimal.valueOf(courseCount), 2, RoundingMode.HALF_UP));
                cr.put("studentCount", grades.size());
                courseRanking.add(cr);
            }
        }

        BigDecimal avgScore = scoreCount > 0
                ? totalScore.divide(BigDecimal.valueOf(scoreCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // 课程排名按平均分降序
        courseRanking.sort((a, b) -> {
            BigDecimal sa = (BigDecimal) a.get("avgScore");
            BigDecimal sb = (BigDecimal) b.get("avgScore");
            return sb.compareTo(sa);
        });

        Map<String, Object> levelDistribution = new LinkedHashMap<>();
        levelDistribution.put("excellent", excellent);
        levelDistribution.put("good", good);
        levelDistribution.put("needImprove", needImprove);

        // 查询班级名称
        String className = "";
        if (classId != null) {
            className = getClassName(classId);
        } else if (!classIdSet.isEmpty()) {
            className = getClassName(classIdSet.iterator().next());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        // 5. 统计实验/实训完成率
        int totalExperimentTasks = 0, completedExperimentTasks = 0;
        int totalTrainingTasks = 0, completedTrainingTasks = 0;

        QueryWrapper<ExperimentTask> taskQw2 = new QueryWrapper<>();
        taskQw2.eq("teacher_id", teacherId)
                .eq("audit_status", AUDIT_APPROVED);
        if (classId != null) taskQw2.eq("class_id", classId);
        else if (!classIdSet.isEmpty()) taskQw2.in("class_id", classIdSet);
        List<ExperimentTask> classTasks = experimentTaskMapper.selectList(taskQw2);

        for (ExperimentTask task : classTasks) {
            ExperimentProject project = experimentProjectMapper.selectById(task.getProjectId());
            boolean isTraining = project != null && "TRAINING".equals(project.getProjectType());

            if (isTraining) totalTrainingTasks++; else totalExperimentTasks++;

            // 统计已完成的学生数
            QueryWrapper<StudentExperimentSubmission> subQw2 = new QueryWrapper<>();
            subQw2.eq("task_id", task.getId()).eq("status", SUBMIT_GRADED);
            long graded = submissionMapper.selectCount(subQw2);
            if (isTraining) completedTrainingTasks += (graded > 0 ? 1 : 0);
            else completedExperimentTasks += (graded > 0 ? 1 : 0);
        }

        BigDecimal avgExperimentRate = totalExperimentTasks > 0
                ? BigDecimal.valueOf(completedExperimentTasks).multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(totalExperimentTasks), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal avgTrainingRate = totalTrainingTasks > 0
                ? BigDecimal.valueOf(completedTrainingTasks).multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(totalTrainingTasks), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        result.put("studentCount", totalStudents);
        result.put("className", className);
        result.put("avgCompletionRate", avgCompletionRate);
        result.put("avgScore", avgScore);
        result.put("avgCorrectRate", 0);       // 习题正确率需对接题库系统，暂返回 0
        result.put("avgExperimentRate", avgExperimentRate);
        result.put("avgTrainingRate", avgTrainingRate);
        result.put("levelDistribution", levelDistribution);
        result.put("courseRanking", courseRanking);

        return result;
    }

    @Override
    public Map<String, Object> getStudentAnalytics(Long userId, Long semester) {
        // 查询学生信息
        SysUser student = sysUserMapper.selectById(userId);
        if (student == null) {
            throw new BusinessException(404, "学生不存在");
        }

        // 1. 查询该学生的所有成绩
        QueryWrapper<StudentGrade> gradeQw = new QueryWrapper<>();
        gradeQw.eq("student_id", userId)
                .eq("is_published", 1);
        if (semester != null) {
            gradeQw.eq("semester_id", semester);
        }
        List<StudentGrade> grades = studentGradeMapper.selectList(gradeQw);

        BigDecimal avgScore = BigDecimal.ZERO;
        if (!grades.isEmpty()) {
            BigDecimal sum = grades.stream()
                    .filter(g -> g.getFinalGrade() != null)
                    .map(StudentGrade::getFinalGrade)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            long count = grades.stream().filter(g -> g.getFinalGrade() != null).count();
            if (count > 0) {
                avgScore = sum.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
            }
        }

        // 2. 查询学习进度
        QueryWrapper<StudentCourseEnrollment> enrollQw = new QueryWrapper<>();
        enrollQw.eq("student_id", userId);
        List<StudentCourseEnrollment> enrollments = enrollmentMapper.selectList(enrollQw);

        BigDecimal avgProgress = BigDecimal.ZERO;
        if (!enrollments.isEmpty()) {
            BigDecimal sumProgress = enrollments.stream()
                    .map(e -> e.getProgressPercent() != null ? e.getProgressPercent() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            avgProgress = sumProgress.divide(BigDecimal.valueOf(enrollments.size()), 2, RoundingMode.HALF_UP);
        }

        // 3. 各科成绩详情
        List<Map<String, Object>> gradeDetails = new ArrayList<>();
        for (StudentGrade g : grades) {
            CoursePlan plan = coursePlanMapper.selectById(g.getCoursePlanId());
            String courseName = "";
            if (plan != null) {
                Course course = courseMapper.selectById(plan.getCourseId());
                courseName = course != null ? course.getCourseName() : "";
            }

            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("coursePlanId", g.getCoursePlanId());
            detail.put("courseName", courseName);
            detail.put("usualGrade", g.getUsualGrade());
            detail.put("examGrade", g.getExamGrade());
            detail.put("experimentGrade", g.getExperimentGrade());
            detail.put("finalGrade", g.getFinalGrade());
            detail.put("gradeComment", g.getGradeComment());
            gradeDetails.add(detail);
        }

        // 4. 任务完成情况
        long totalTasks = 0;
        long completedTasks = 0;
        List<ExperimentTask> allTasks = experimentTaskMapper.selectList(null);
        for (ExperimentTask task : allTasks) {
            QueryWrapper<StudentExperimentSubmission> subQw = new QueryWrapper<>();
            subQw.eq("task_id", task.getId())
                    .eq("student_id", userId);
            StudentExperimentSubmission sub = submissionMapper.selectOne(subQw);
            if (sub != null && (SUBMIT_SUBMITTED.equals(sub.getStatus())
                    || SUBMIT_GRADED.equals(sub.getStatus()))) {
                completedTasks++;
            }
            totalTasks++;
        }

        BigDecimal taskCompletionRate = totalTasks > 0
                ? BigDecimal.valueOf(completedTasks)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalTasks), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // 5. 诊断信息
        String level;
        String diagnosis;
        if (avgScore.compareTo(BigDecimal.valueOf(90)) >= 0) {
            level = "优秀";
            diagnosis = "学习成绩优秀，学习状态良好。";
        } else if (avgScore.compareTo(BigDecimal.valueOf(80)) >= 0) {
            level = "良好";
            diagnosis = "学习成绩良好，有提升空间。";
        } else if (avgScore.compareTo(BigDecimal.valueOf(70)) >= 0) {
            level = "中等";
            diagnosis = "学习成绩中等，需加强学习。";
        } else if (avgScore.compareTo(BigDecimal.valueOf(60)) >= 0) {
            level = "及格";
            diagnosis = "学习成绩偏低，需加倍努力。";
        } else {
            level = "需努力";
            diagnosis = "学习成绩不理想，需要重点关注。";
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", userId);
        result.put("studentName", student.getRealName() != null ? student.getRealName() : student.getUsername());
        result.put("className", student.getClassId() != null ? getClassName(student.getClassId()) : "");
        result.put("avgScore", avgScore);
        result.put("avgProgress", avgProgress);
        result.put("taskCompletionRate", taskCompletionRate);
        result.put("completedTasks", completedTasks);
        result.put("totalTasks", totalTasks);
        result.put("gradeDetails", gradeDetails);
        result.put("level", level);
        result.put("diagnosis", diagnosis);

        return result;
    }

    @Override
    public IPage<Map<String, Object>> getAtRiskStudents(Long classId, String filterType, Integer page, Integer pageSize) {
        Long teacherId = UserContext.getUserId();

        // 翻译前端过滤器值到后端常量
        if ("MISSING".equals(filterType)) {
            filterType = "MISSING_HOMEWORK";
        } else if ("SLOW".equals(filterType)) {
            filterType = "SLOW_PROGRESS";
        }

        // 查询该教师教授的课程对应的班级
        QueryWrapper<CoursePlan> planQw = new QueryWrapper<>();
        planQw.eq("teacher_id", teacherId)
                .eq("audit_status", AUDIT_APPROVED);
        if (classId != null) {
            planQw.eq("class_id", classId);
        }
        List<CoursePlan> myPlans = coursePlanMapper.selectList(planQw);

        Set<Long> targetClassIds = myPlans.stream()
                .map(CoursePlan::getClassId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (targetClassIds.isEmpty()) {
            return new Page<>(page, pageSize, 0);
        }

        // 查询这些班级的所有学生
        QueryWrapper<SysUser> stuQw = new QueryWrapper<>();
        stuQw.in("class_id", targetClassIds);
        List<SysUser> students = sysUserMapper.selectList(stuQw);

        List<Map<String, Object>> riskList = new ArrayList<>();

        for (SysUser student : students) {
            // 总体风险分析
            boolean isAtRisk = false;
            String riskReason = "";

            // 查询该学生的所有选课记录
            List<StudentCourseEnrollment> enrollments = new ArrayList<>();
            for (CoursePlan plan : myPlans) {
                QueryWrapper<StudentCourseEnrollment> enrollQw = new QueryWrapper<>();
                enrollQw.eq("student_id", student.getId())
                        .eq("course_plan_id", plan.getId());
                StudentCourseEnrollment enroll = enrollmentMapper.selectOne(enrollQw);
                if (enroll != null) {
                    enrollments.add(enroll);
                }
            }

            // 查询该学生的所有实验提交
            List<ExperimentTask> teacherTasks = experimentTaskMapper.selectList(
                    new QueryWrapper<ExperimentTask>().eq("teacher_id", teacherId));
            long missedTaskCount = 0;
            for (ExperimentTask task : teacherTasks) {
                if (task.getClassId() != null && !task.getClassId().equals(student.getClassId())) {
                    continue;
                }
                QueryWrapper<StudentExperimentSubmission> subQw = new QueryWrapper<>();
                subQw.eq("task_id", task.getId())
                        .eq("student_id", student.getId());
                StudentExperimentSubmission sub = submissionMapper.selectOne(subQw);
                if (sub == null || SUBMIT_RETURNED.equals(sub.getStatus())) {
                    missedTaskCount++;
                }
            }

            // 查询平均分
            BigDecimal avgScore = BigDecimal.ZERO;
            int scoreCount = 0;
            for (CoursePlan plan : myPlans) {
                QueryWrapper<StudentGrade> gQw = new QueryWrapper<>();
                gQw.eq("student_id", student.getId())
                        .eq("course_plan_id", plan.getId())
                        .eq("is_published", 1);
                StudentGrade g = studentGradeMapper.selectOne(gQw);
                if (g != null && g.getFinalGrade() != null) {
                    avgScore = avgScore.add(g.getFinalGrade());
                    scoreCount++;
                }
            }
            if (scoreCount > 0) {
                avgScore = avgScore.divide(BigDecimal.valueOf(scoreCount), 2, RoundingMode.HALF_UP);
            }

            // 平均进度
            BigDecimal avgProgress = BigDecimal.ZERO;
            if (!enrollments.isEmpty()) {
                BigDecimal sum = enrollments.stream()
                        .map(e -> e.getProgressPercent() != null ? e.getProgressPercent() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                avgProgress = sum.divide(BigDecimal.valueOf(enrollments.size()), 2, RoundingMode.HALF_UP);
            }

            // 根据预警类型判断
            if ("MISSING_HOMEWORK".equals(filterType) || "ALL".equals(filterType)) {
                if (missedTaskCount > 0) {
                    isAtRisk = true;
                    riskReason = "有 " + missedTaskCount + " 个实验任务未提交或已被退回";
                }
            }
            if ("LOW_SCORE".equals(filterType) || "ALL".equals(filterType)) {
                if (scoreCount > 0 && avgScore.compareTo(BigDecimal.valueOf(60)) < 0) {
                    isAtRisk = true;
                    riskReason = "平均分 " + avgScore + "，低于 60 分";
                }
            }
            if ("SLOW_PROGRESS".equals(filterType) || "ALL".equals(filterType)) {
                if (!enrollments.isEmpty() && avgProgress.compareTo(BigDecimal.valueOf(30)) < 0) {
                    isAtRisk = true;
                    riskReason = "平均学习进度仅 " + avgProgress + "%，低于30%";
                }
            }

            if (isAtRisk) {
                // 计算风险等级
                String riskLevel = null;
                if (avgScore.compareTo(BigDecimal.valueOf(60)) < 0 || missedTaskCount >= 3) {
                    riskLevel = "HIGH";
                } else if (avgProgress.compareTo(BigDecimal.valueOf(50)) < 0 || missedTaskCount >= 1) {
                    riskLevel = "MEDIUM";
                }

                Map<String, Object> risk = new LinkedHashMap<>();
                risk.put("userId", student.getId());
                risk.put("userName", student.getRealName() != null ? student.getRealName() : student.getUsername());
                risk.put("className", student.getClassId() != null ? getClassName(student.getClassId()) : "");
                risk.put("avgScore", avgScore);
                risk.put("completionRate", avgProgress);
                risk.put("missedTasks", missedTaskCount);
                risk.put("riskLevel", riskLevel);
                risk.put("riskReason", riskReason);
                riskList.add(risk);
            }
        }

        // 内存分页
        int total = riskList.size();
        int fromIndex = (page - 1) * pageSize;
        if (fromIndex >= total) {
            fromIndex = 0;
        }
        int toIndex = Math.min(fromIndex + pageSize, total);
        List<Map<String, Object>> pageList = riskList.subList(fromIndex, toIndex);

        IPage<Map<String, Object>> result = new Page<>(page, pageSize, total);
        result.setRecords(pageList);
        return result;
    }

    // ==================== 通知公告 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createNotice(Long classId, String title, String content, String importance) {
        Long teacherId = UserContext.getUserId();

        SysAnnouncement announcement = new SysAnnouncement();
        announcement.setTitle(title);
        announcement.setContent(content);
        announcement.setAnnounceType("CLASS");
        announcement.setTargetClassId(classId);
        announcement.setPublisherId(teacherId);
        announcement.setStatus(1);
        announcement.setPublishTime(LocalDateTime.now());

        if ("HIGH".equalsIgnoreCase(importance) || "IMPORTANT".equalsIgnoreCase(importance)) {
            announcement.setIsTop(1);
        } else {
            announcement.setIsTop(0);
        }

        sysAnnouncementMapper.insert(announcement);

        log.info("教师 {} 发布了一条班级公告（班级ID: {}）", teacherId, classId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("noticeId", announcement.getId());
        result.put("title", announcement.getTitle());
        result.put("publishTime", announcement.getPublishTime());

        return result;
    }

    @Override
    public IPage<Map<String, Object>> getNotices(Long classId, Integer page, Integer pageSize) {
        Long teacherId = UserContext.getUserId();

        Page<SysAnnouncement> annPage = new Page<>(page, pageSize);
        QueryWrapper<SysAnnouncement> annQw = new QueryWrapper<>();
        annQw.eq("publisher_id", teacherId);

        if (classId != null) {
            annQw.eq("target_class_id", classId);
        }
        annQw.orderByDesc("is_top").orderByDesc("publish_time");

        IPage<SysAnnouncement> annPageResult = sysAnnouncementMapper.selectPage(annPage, annQw);
        List<SysAnnouncement> announcements = annPageResult.getRecords();

        List<Map<String, Object>> noticeList = new ArrayList<>();
        for (SysAnnouncement ann : announcements) {
            SysClass sysClass = ann.getTargetClassId() != null
                    ? sysClassMapper.selectById(ann.getTargetClassId()) : null;

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("noticeId", ann.getId());
            item.put("title", ann.getTitle());
            item.put("content", ann.getContent());
            item.put("classId", ann.getTargetClassId());
            item.put("className", sysClass != null ? sysClass.getClassName() : "");
            item.put("importance", ann.getIsTop() != null && ann.getIsTop() == 1 ? "IMPORTANT" : "NORMAL");
            item.put("isTop", ann.getIsTop());
            item.put("readCount", 0);  // 暂未跟踪已读数
            item.put("publishTime", ann.getPublishTime());
            item.put("createTime", ann.getCreateTime());
            noticeList.add(item);
        }

        Page<Map<String, Object>> resultPage = new Page<>(annPage.getCurrent(), annPage.getSize());
        resultPage.setTotal(annPageResult.getTotal());
        resultPage.setRecords(noticeList);

        return resultPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteNotice(Long noticeId) {
        Long teacherId = UserContext.getUserId();

        SysAnnouncement announcement = sysAnnouncementMapper.selectById(noticeId);
        if (announcement == null) {
            throw new BusinessException(404, "公告不存在");
        }

        // 校验是否为本人发布的公告
        if (!teacherId.equals(announcement.getPublisherId())) {
            throw new BusinessException(403, "无权删除该公告");
        }

        sysAnnouncementMapper.deleteById(noticeId);
        log.info("教师 {} 删除了公告 {}", teacherId, noticeId);
    }

    // ==================== 消息 ====================

    @Override
    public Map<String, Object> getTeacherMessages(Integer page, Integer pageSize) {
        Long teacherId = UserContext.getUserId();

        // 1. 查询未读消息数
        QueryWrapper<SysMessage> unreadQw = new QueryWrapper<>();
        unreadQw.eq("receiver_id", teacherId)
                .eq("is_read", 0);
        long unreadCount = sysMessageMapper.selectCount(unreadQw);

        // 2. 分页查询消息列表
        Page<SysMessage> msgPage = new Page<>(page, pageSize);
        QueryWrapper<SysMessage> msgQw = new QueryWrapper<>();
        msgQw.eq("receiver_id", teacherId);
        msgQw.orderByDesc("create_time");

        IPage<SysMessage> msgPageResult = sysMessageMapper.selectPage(msgPage, msgQw);

        // 将实体列表转换为前端字段名的 Map 列表
        List<Map<String, Object>> msgList = new ArrayList<>();
        for (SysMessage msg : msgPageResult.getRecords()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("messageId", msg.getId());
            m.put("title", msg.getTitle());
            m.put("content", msg.getContent());
            m.put("type", msg.getMessageType());
            m.put("isRead", msg.getIsRead());
            m.put("sendTime", msg.getCreateTime());
            msgList.add(m);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("unreadCount", unreadCount);
        result.put("records", msgList);
        result.put("total", msgPageResult.getTotal());
        result.put("page", msgPageResult.getCurrent());
        result.put("pageSize", msgPageResult.getSize());

        return result;
    }

    // ==================== 消息已读标记 ====================

    @Override
    public void markMessageAsRead(Long messageId) {
        Long teacherId = UserContext.getUserId();

        SysMessage message = sysMessageMapper.selectById(messageId);
        if (message == null) {
            throw new BusinessException(404, "消息不存在");
        }
        // 校验消息归属
        if (!teacherId.equals(message.getReceiverId())) {
            throw new BusinessException(403, "无权操作该消息");
        }

        message.setIsRead(1);
        message.setReadTime(LocalDateTime.now());
        sysMessageMapper.updateById(message);

        log.info("教师 {} 标记消息 {} 为已读", teacherId, messageId);
    }

    @Override
    public void markAllMessagesAsRead() {
        Long teacherId = UserContext.getUserId();

        // 查询所有未读消息
        QueryWrapper<SysMessage> unreadQw = new QueryWrapper<>();
        unreadQw.eq("receiver_id", teacherId)
                .eq("is_read", 0);
        List<SysMessage> unreadMessages = sysMessageMapper.selectList(unreadQw);

        if (unreadMessages.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        for (SysMessage msg : unreadMessages) {
            msg.setIsRead(1);
            msg.setReadTime(now);
            sysMessageMapper.updateById(msg);
        }

        log.info("教师 {} 将所有 {} 条消息标记为已读", teacherId, unreadMessages.size());
    }

    // ==================== 导出/上传辅助功能 ====================

    @Override
    public byte[] exportGrades(Long courseId) {
        // 1. 查询课程信息
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new BusinessException(404, "课程不存在");
        }

        // 2. 获取 CoursePlan
        QueryWrapper<CoursePlan> planQw = new QueryWrapper<>();
        planQw.eq("course_id", courseId);
        CoursePlan plan = coursePlanMapper.selectOne(planQw);
        if (plan == null) {
            throw new BusinessException(404, "开课计划不存在");
        }

        // 3. 查询该课程所有学生的成绩
        QueryWrapper<StudentGrade> gradeQw = new QueryWrapper<>();
        gradeQw.eq("course_plan_id", plan.getId());
        List<StudentGrade> grades = studentGradeMapper.selectList(gradeQw);

        // 4. 查询班级名称
        String className = "";
        if (plan.getClassId() != null) {
            SysClass sysClass = sysClassMapper.selectById(plan.getClassId());
            className = sysClass != null ? sysClass.getClassName() : "";
        }

        // 5. 使用 Apache POI 生成 Excel
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(course.getCourseName() + " 成绩表");

            // ---- 表头样式 ----
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // ---- 标题行 ----
            Row titleRow = sheet.createRow(0);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue(course.getCourseName() + " — 成绩表");
            titleCell.setCellStyle(headerStyle);
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 7));

            // 班级信息行
            Row infoRow = sheet.createRow(1);
            infoRow.createCell(0).setCellValue("班级：" + className);
            infoRow.createCell(1).setCellValue("总人数：" + grades.size());

            // ---- 表头 ----
            Row headerRow = sheet.createRow(3);
            String[] headers = {"序号", "学号", "姓名", "平时成绩", "考试成绩", "实验成绩", "最终成绩", "评语"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // ---- 数据行 ----
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            for (int i = 0; i < grades.size(); i++) {
                StudentGrade grade = grades.get(i);
                Row row = sheet.createRow(i + 4);

                row.createCell(0).setCellValue(i + 1);

                // 查询学生信息
                SysUser student = sysUserMapper.selectById(grade.getStudentId());
                row.createCell(1).setCellValue(student != null ? (student.getUsername() != null ? student.getUsername() : "") : "");
                row.createCell(2).setCellValue(student != null ? (student.getRealName() != null ? student.getRealName() : "") : "");

                row.createCell(3).setCellValue(grade.getUsualGrade() != null ? grade.getUsualGrade().doubleValue() : 0);
                row.createCell(4).setCellValue(grade.getExamGrade() != null ? grade.getExamGrade().doubleValue() : 0);
                row.createCell(5).setCellValue(grade.getExperimentGrade() != null ? grade.getExperimentGrade().doubleValue() : 0);
                row.createCell(6).setCellValue(grade.getFinalGrade() != null ? grade.getFinalGrade().doubleValue() : 0);
                row.createCell(7).setCellValue(grade.getGradeComment() != null ? grade.getGradeComment() : "");
            }

            // 自动调整列宽
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // 写入字节数组
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            log.info("教师 {} 导出了课程 {} 的成绩，共 {} 条", UserContext.getUserId(), courseId, grades.size());
            return baos.toByteArray();

        } catch (IOException e) {
            log.error("导出成绩 Excel 失败：{}", e.getMessage(), e);
            throw new BusinessException(500, "导出失败：" + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void uploadGuideFile(Long taskId, String fileUrl, String fileName) {
        Long teacherId = UserContext.getUserId();

        ExperimentTask task = experimentTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(404, "实验任务不存在");
        }

        // 校验是否为该教师的任务
        if (!teacherId.equals(task.getTeacherId())) {
            throw new BusinessException(403, "无权操作该任务");
        }

        // 更新关联的项目指导文件
        if (task.getProjectId() != null) {
            ExperimentProject project = experimentProjectMapper.selectById(task.getProjectId());
            if (project != null) {
                project.setGuideFileUrl(fileUrl);
                experimentProjectMapper.updateById(project);
                log.info("教师 {} 为任务 {} 上传了指导文件：{}", teacherId, taskId, fileName);
            }
        }
    }

    @Override
    public Map<String, Object> getAuditFeedback(Long resourceId) {
        TeachingResource resource = teachingResourceMapper.selectById(resourceId);
        if (resource == null) {
            throw new BusinessException(404, "教学资源不存在");
        }

        // 查询该资源的所有审核日志
        QueryWrapper<SysAuditLog> logQw = new QueryWrapper<>();
        logQw.eq("biz_type", "RESOURCE")
                .eq("biz_id", resourceId)
                .orderByDesc("create_time");
        List<SysAuditLog> auditLogs = sysAuditLogMapper.selectList(logQw);

        List<Map<String, Object>> logList = new ArrayList<>();
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        for (SysAuditLog auditLog : auditLogs) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("logId", auditLog.getId());
            item.put("action", auditLog.getAction());
            item.put("operatorName", auditLog.getOperatorName());
            item.put("beforeStatus", auditLog.getBeforeStatus());
            item.put("afterStatus", auditLog.getAfterStatus());
            item.put("comment", auditLog.getComment());
            item.put("createTime", auditLog.getCreateTime() != null
                    ? auditLog.getCreateTime().format(dtf) : null);
            logList.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("resourceId", resourceId);
        result.put("resourceName", resource.getResourceName());
        result.put("currentStatus", resource.getAuditStatus());
        result.put("auditComment", resource.getAuditComment());
        result.put("auditLogs", logList);

        return result;
    }

    @Override
    public byte[] exportAnalytics(Long classId, Long semester) {
        // 复用 getClassOverview 的数据，然后写入 Excel
        Map<String, Object> overview = getClassOverview(classId, semester);

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("学情分析报表");

            // ---- 样式 ----
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            CellStyle labelStyle = workbook.createCellStyle();
            Font labelFont = workbook.createFont();
            labelFont.setBold(true);
            labelStyle.setFont(labelFont);

            int rowIdx = 0;

            // ---- 概览信息 ----
            Row titleRow = sheet.createRow(rowIdx++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("班级学情分析报表");
            titleCell.setCellStyle(headerStyle);
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 3));
            rowIdx++;

            Row totalRow = sheet.createRow(rowIdx++);
            totalRow.createCell(0).setCellValue("总学生数");
            totalRow.getCell(0).setCellStyle(labelStyle);
            totalRow.createCell(1).setCellValue(((Number) overview.get("totalStudents")).doubleValue());

            Row completeRow = sheet.createRow(rowIdx++);
            completeRow.createCell(0).setCellValue("平均完成率");
            completeRow.getCell(0).setCellStyle(labelStyle);
            completeRow.createCell(1).setCellValue(((BigDecimal) overview.get("avgCompletionRate")).doubleValue() + "%");

            Row scoreRow = sheet.createRow(rowIdx++);
            scoreRow.createCell(0).setCellValue("平均分");
            scoreRow.getCell(0).setCellStyle(labelStyle);
            scoreRow.createCell(1).setCellValue(((BigDecimal) overview.get("avgScore")).doubleValue());

            rowIdx++;

            // ---- 等级分布 ----
            @SuppressWarnings("unchecked")
            Map<String, Object> levelDist = (Map<String, Object>) overview.get("levelDistribution");
            if (levelDist != null) {
                Row levelHeader = sheet.createRow(rowIdx++);
                levelHeader.createCell(0).setCellValue("等级分布");
                levelHeader.getCell(0).setCellStyle(headerStyle);
                sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowIdx - 1, rowIdx - 1, 0, 1));

                Row excellentRow = sheet.createRow(rowIdx++);
                excellentRow.createCell(0).setCellValue("优秀 (≥90)");
                excellentRow.createCell(1).setCellValue(levelDist.get("excellent") != null
                        ? ((Number) levelDist.get("excellent")).doubleValue() : 0);

                Row goodRow = sheet.createRow(rowIdx++);
                goodRow.createCell(0).setCellValue("良好 (≥80)");
                goodRow.createCell(1).setCellValue(levelDist.get("good") != null
                        ? ((Number) levelDist.get("good")).doubleValue() : 0);

                Row improveRow = sheet.createRow(rowIdx++);
                improveRow.createCell(0).setCellValue("待提升 (<80)");
                improveRow.createCell(1).setCellValue(levelDist.get("needImprove") != null
                        ? ((Number) levelDist.get("needImprove")).doubleValue() : 0);
            }

            rowIdx++;

            // ---- 课程排名 ----
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> courseRanking = (List<Map<String, Object>>) overview.get("courseRanking");
            if (courseRanking != null && !courseRanking.isEmpty()) {
                Row rankHeader = sheet.createRow(rowIdx++);
                String[] rankHeaders = {"排名", "课程名称", "平均分", "学生数"};
                for (int i = 0; i < rankHeaders.length; i++) {
                    Cell cell = rankHeader.createCell(i);
                    cell.setCellValue(rankHeaders[i]);
                    cell.setCellStyle(headerStyle);
                }

                for (int i = 0; i < courseRanking.size(); i++) {
                    Map<String, Object> cr = courseRanking.get(i);
                    Row row = sheet.createRow(rowIdx++);
                    row.createCell(0).setCellValue(i + 1);
                    row.createCell(1).setCellValue(cr.get("courseName") != null ? cr.get("courseName").toString() : "");
                    row.createCell(2).setCellValue(cr.get("avgScore") != null
                            ? ((BigDecimal) cr.get("avgScore")).doubleValue() : 0);
                    row.createCell(3).setCellValue(cr.get("studentCount") != null
                            ? ((Number) cr.get("studentCount")).doubleValue() : 0);
                }
            }

            // 自动调整列宽
            for (int i = 0; i < 4; i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            log.info("教师 {} 导出了学情分析报表", UserContext.getUserId());
            return baos.toByteArray();

        } catch (IOException e) {
            log.error("导出学情分析 Excel 失败：{}", e.getMessage(), e);
            throw new BusinessException(500, "导出失败：" + e.getMessage());
        }
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 根据班级 ID 获取班级名称
     */
    private String getClassName(Long classId) {
        SysClass sysClass = sysClassMapper.selectById(classId);
        return sysClass != null ? sysClass.getClassName() : "";
    }

    /**
     * 根据学期名称（如 "2025-2026-2"）查找学期 ID
     */
    private Long resolveSemesterId(String semesterName) {
        if (semesterName == null || semesterName.isEmpty()) return null;
        QueryWrapper<SysSemester> qw = new QueryWrapper<>();
        qw.eq("semester_name", semesterName);
        SysSemester sem = sysSemesterMapper.selectOne(qw);
        return sem != null ? sem.getId() : null;
    }

    /**
     * 根据班级名称查找班级 ID
     */
    private Long resolveClassId(String className) {
        if (className == null || className.isEmpty()) return null;
        // 先用精确匹配
        QueryWrapper<SysClass> qw = new QueryWrapper<>();
        qw.eq("class_name", className);
        SysClass cls = sysClassMapper.selectOne(qw);
        if (cls != null) return cls.getId();
        // 精确匹配失败 → 模糊匹配（支持前端传简称如"软件工程"找到"软件工程2101班"）
        QueryWrapper<SysClass> likeQw = new QueryWrapper<>();
        likeQw.like("class_name", className);
        SysClass likeCls = sysClassMapper.selectOne(likeQw);
        return likeCls != null ? likeCls.getId() : null;
    }

    /**
     * 根据课程名称查找课程 ID（返回第一个匹配）
     */
    private Long resolveCourseId(String courseName) {
        if (courseName == null || courseName.isEmpty()) return null;
        QueryWrapper<Course> qw = new QueryWrapper<>();
        qw.eq("course_name", courseName);
        Course course = courseMapper.selectOne(qw);
        return course != null ? course.getId() : null;
    }

    /**
     * 计算学生某类型任务的平均分（只统计已批阅成绩）
     */
    private BigDecimal calcAvgTaskScore(Long studentId, List<ExperimentTask> courseTasks,
                                         Map<Long, String> taskTypeMap, String type) {
        BigDecimal total = BigDecimal.ZERO;
        int count = 0;
        for (ExperimentTask task : courseTasks) {
            if (!type.equals(taskTypeMap.get(task.getId()))) continue;
            QueryWrapper<StudentExperimentSubmission> subQw = new QueryWrapper<>();
            subQw.eq("task_id", task.getId())
                    .eq("student_id", studentId)
                    .eq("status", "GRADED")
                    .isNotNull("score");
            StudentExperimentSubmission sub = submissionMapper.selectOne(subQw);
            if (sub != null && sub.getScore() != null) {
                total = total.add(sub.getScore());
                count++;
            }
        }
        return count > 0 ? total.divide(BigDecimal.valueOf(count), 1, RoundingMode.HALF_UP) : null;
    }

    /**
     * 兼容读取输入字段：优先用前端 key，fallback 用后端 key
     */
    private Object readInputField(Map<String, Object> data, String frontendKey, String backendKey) {
        if (data.containsKey(frontendKey)) return data.get(frontendKey);
        return data.get(backendKey);
    }
}
