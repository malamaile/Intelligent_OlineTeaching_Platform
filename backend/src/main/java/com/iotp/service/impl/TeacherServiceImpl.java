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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 教师端业务服务实现
 *
 * @author 杨雨洁
 * @since 2026-06-18
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

    // ==================== 首页看板 ====================

    @Override
    public Map<String, Object> getDashboard() {
        Long teacherId = UserContext.getUserId();

        // 1. 待批阅统计：查询该教师发布的实验任务，统计学生提交状态为 SUBMITTED 的记录
        Map<String, Object> pendingReview = new LinkedHashMap<>();

        // 1.1 查询该教师所有的实验任务 ID
        QueryWrapper<ExperimentTask> taskQw = new QueryWrapper<>();
        taskQw.eq("teacher_id", teacherId);
        List<ExperimentTask> myTasks = experimentTaskMapper.selectList(taskQw);
        Set<Long> taskIds = myTasks.stream().map(ExperimentTask::getId).collect(Collectors.toSet());

        long experimentPending = 0; // 实验待批阅
        long trainingPending = 0;   // 实训待批阅

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
                        trainingPending++;
                    } else {
                        experimentPending++;
                    }
                }
            }
        }

        pendingReview.put("experimentPending", experimentPending);
        pendingReview.put("trainingPending", trainingPending);

        // 2. 待审核统计：课程 / 实验任务 / 教学资源
        Map<String, Object> pendingAudit = new LinkedHashMap<>();

        // 2.1 待审核开课计划
        QueryWrapper<CoursePlan> planAuditQw = new QueryWrapper<>();
        planAuditQw.eq("teacher_id", teacherId)
                .eq("audit_status", AUDIT_PENDING);
        long pendingCoursePlans = coursePlanMapper.selectCount(planAuditQw);
        pendingAudit.put("pendingCoursePlans", pendingCoursePlans);

        // 2.2 待审核实验任务
        QueryWrapper<ExperimentTask> taskAuditQw = new QueryWrapper<>();
        taskAuditQw.eq("teacher_id", teacherId)
                .eq("audit_status", AUDIT_PENDING);
        long pendingTasks = experimentTaskMapper.selectCount(taskAuditQw);
        pendingAudit.put("pendingTasks", pendingTasks);

        // 2.3 待审核教学资源
        QueryWrapper<TeachingResource> resAuditQw = new QueryWrapper<>();
        resAuditQw.eq("teacher_id", teacherId)
                .eq("audit_status", AUDIT_PENDING);
        long pendingResources = teachingResourceMapper.selectCount(resAuditQw);
        pendingAudit.put("pendingResources", pendingResources);

        // 3. 预警学生（简化实现：统计有未提交任务的学生）
        List<Map<String, Object>> atRiskStudents = new ArrayList<>();
        if (!taskIds.isEmpty()) {
            // 查询所有有提交记录的任务，找到未提交的学生
            for (ExperimentTask task : myTasks) {
                if (task.getClassId() == null) continue;

                // 查询该班级所有学生
                QueryWrapper<SysUser> studentQw = new QueryWrapper<>();
                studentQw.eq("class_id", task.getClassId());
                List<SysUser> students = sysUserMapper.selectList(studentQw);

                for (SysUser student : students) {
                    // 检查该学生是否提交了该任务
                    QueryWrapper<StudentExperimentSubmission> checkSub = new QueryWrapper<>();
                    checkSub.eq("task_id", task.getId())
                            .eq("student_id", student.getId());
                    StudentExperimentSubmission sub = submissionMapper.selectOne(checkSub);

                    if (sub == null || SUBMIT_RETURNED.equals(sub.getStatus())) {
                        // 未提交或退回，视为预警
                        Map<String, Object> risk = new LinkedHashMap<>();
                        risk.put("studentId", student.getId());
                        risk.put("studentName", student.getRealName() != null ? student.getRealName() : student.getUsername());
                        risk.put("reason", sub == null ? "未提交实验任务" : "实验任务被退回");
                        risk.put("taskId", task.getId());
                        atRiskStudents.add(risk);
                    }
                }
            }
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
            item.put("title", msg.getTitle());
            item.put("content", msg.getContent());
            item.put("isRead", msg.getIsRead());
            item.put("time", msg.getCreateTime());
            notifications.add(item);
        }

        // 5. 班级概况
        Map<String, Object> classSummary = new LinkedHashMap<>();
        // 查询该教师负责的班级（通过开课计划）
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
            // 统计班级总学生数
            QueryWrapper<SysUser> stuQw = new QueryWrapper<>();
            stuQw.in("class_id", classIds);
            totalStudents = sysUserMapper.selectCount(stuQw);

            // 统计平均完成率（从选课记录）
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

            // 统计平均分
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
        result.put("pendingReview", pendingReview);
        result.put("pendingAudit", pendingAudit);
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

            Map<String, Object> courseMap = new LinkedHashMap<>();
            courseMap.put("planId", plan.getId());
            courseMap.put("courseId", course.getId());
            courseMap.put("courseName", course.getCourseName());
            courseMap.put("courseCode", course.getCourseCode());
            courseMap.put("description", course.getDescription());
            courseMap.put("coverImage", course.getCoverImage());
            courseMap.put("departmentId", course.getDepartmentId());
            courseMap.put("semesterId", plan.getSemesterId());
            courseMap.put("semesterName", sem != null ? sem.getSemesterName() : "");
            courseMap.put("classId", plan.getClassId());
            courseMap.put("className", sysClass != null ? sysClass.getClassName() : "");
            courseMap.put("scheduleInfo", plan.getScheduleInfo());
            courseMap.put("auditStatus", plan.getAuditStatus());
            courseMap.put("auditComment", plan.getAuditComment());
            courseMap.put("createTime", plan.getCreateTime());

            courseList.add(courseMap);
        }

        Page<Map<String, Object>> resultPage = new Page<>(planPage.getCurrent(), planPage.getSize());
        resultPage.setTotal(planPageResult.getTotal());
        resultPage.setRecords(courseList);

        return resultPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createCourse(Map<String, Object> courseData) {
        Long teacherId = UserContext.getUserId();
        String teacherName = UserContext.getUsername();

        // 1. 创建 Course
        Course course = new Course();
        course.setCourseName((String) courseData.get("courseName"));
        course.setCourseCode((String) courseData.get("courseCode"));
        course.setDescription((String) courseData.get("description"));
        course.setCoverImage((String) courseData.get("coverImage"));
        if (courseData.containsKey("departmentId")) {
            course.setDepartmentId(Long.valueOf(courseData.get("departmentId").toString()));
        }
        course.setTeacherId(teacherId);
        course.setAuditStatus(AUDIT_PENDING);
        course.setStatus(1);

        courseMapper.insert(course);

        // 2. 创建 CoursePlan
        CoursePlan plan = new CoursePlan();
        plan.setCourseId(course.getId());
        if (courseData.containsKey("semesterId")) {
            plan.setSemesterId(Long.valueOf(courseData.get("semesterId").toString()));
        }
        if (courseData.containsKey("classId")) {
            plan.setClassId(Long.valueOf(courseData.get("classId").toString()));
        }
        plan.setTeacherId(teacherId);
        plan.setScheduleInfo((String) courseData.get("scheduleInfo"));
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
            if (courseData.containsKey("semesterId")) {
                plan.setSemesterId(Long.valueOf(courseData.get("semesterId").toString()));
            }
            if (courseData.containsKey("classId")) {
                plan.setClassId(Long.valueOf(courseData.get("classId").toString()));
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
                sp.put("studentName", student.getRealName() != null ? student.getRealName() : student.getUsername());
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
            Long studentId = Long.valueOf(gradeEntry.get("studentId").toString());

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
            if (gradeEntry.containsKey("experimentGrade")) {
                grade.setExperimentGrade(new BigDecimal(gradeEntry.get("experimentGrade").toString()));
            }
            if (gradeEntry.containsKey("trainingGrade")) {
                grade.setTrainingGrade(new BigDecimal(gradeEntry.get("trainingGrade").toString()));
            }

            // 计算最终成绩：平时*0.3 + 考试*0.4 + 实验*0.3（仅当字段都存在时）
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

            if (courseId != null && project != null && !courseId.equals(project.getId())) {
                // 这里 courseId 比较的是 experiment_project 中的关联课程，简化处理略过
            }

            SysClass sysClass = task.getClassId() != null ? sysClassMapper.selectById(task.getClassId()) : null;

            Map<String, Object> taskMap = new LinkedHashMap<>();
            taskMap.put("taskId", task.getId());
            taskMap.put("projectId", task.getProjectId());
            taskMap.put("projectName", project != null ? project.getProjectName() : "");
            taskMap.put("projectType", project != null ? project.getProjectType() : "");
            taskMap.put("description", project != null ? project.getDescription() : "");
            taskMap.put("classId", task.getClassId());
            taskMap.put("className", sysClass != null ? sysClass.getClassName() : "");
            taskMap.put("startTime", task.getStartTime());
            taskMap.put("endTime", task.getEndTime());
            taskMap.put("status", task.getStatus());
            taskMap.put("auditStatus", task.getAuditStatus());

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
        project.setProjectName((String) taskData.get("projectName"));
        project.setDescription((String) taskData.get("description"));
        project.setProjectType((String) taskData.get("projectType"));
        project.setTeacherId(teacherId);
        project.setGuideFileUrl((String) taskData.get("guideFileUrl"));
        project.setAuditStatus(AUDIT_PENDING);

        experimentProjectMapper.insert(project);

        // 2. 创建实验任务
        ExperimentTask task = new ExperimentTask();
        task.setProjectId(project.getId());
        if (taskData.containsKey("classId")) {
            task.setClassId(Long.valueOf(taskData.get("classId").toString()));
        }
        task.setTeacherId(teacherId);
        task.setStartTime(taskData.get("startTime") != null
                ? LocalDateTime.parse(taskData.get("startTime").toString().replace(" ", "T")) : LocalDateTime.now());
        task.setEndTime(taskData.get("endTime") != null
                ? LocalDateTime.parse(taskData.get("endTime").toString().replace(" ", "T")) : null);
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
        if (taskData.containsKey("classId")) {
            task.setClassId(Long.valueOf(taskData.get("classId").toString()));
        }
        if (taskData.containsKey("startTime")) {
            task.setStartTime(LocalDateTime.parse(taskData.get("startTime").toString().replace(" ", "T")));
        }
        if (taskData.containsKey("endTime")) {
            task.setEndTime(LocalDateTime.parse(taskData.get("endTime").toString().replace(" ", "T")));
        }
        if (taskData.containsKey("status")) {
            task.setStatus((String) taskData.get("status"));
        }
        experimentTaskMapper.updateById(task);

        // 更新项目信息
        if (task.getProjectId() != null) {
            ExperimentProject project = experimentProjectMapper.selectById(task.getProjectId());
            if (project != null) {
                if (taskData.containsKey("projectName")) {
                    project.setProjectName((String) taskData.get("projectName"));
                }
                if (taskData.containsKey("description")) {
                    project.setDescription((String) taskData.get("description"));
                }
                if (taskData.containsKey("projectType")) {
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
            item.put("studentName", student != null ? (student.getRealName() != null ? student.getRealName() : student.getUsername()) : "");
            item.put("processDescription", sub.getProcessDescription());
            item.put("reportFileUrl", sub.getReportFileUrl());
            item.put("reportFileName", sub.getReportFileName());
            item.put("score", sub.getScore());
            item.put("teacherComment", sub.getTeacherComment());
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
            resMap.put("viewCount", res.getViewCount());
            resMap.put("downloadCount", res.getDownloadCount());
            resMap.put("auditStatus", res.getAuditStatus());
            resMap.put("auditComment", res.getAuditComment());
            resMap.put("createTime", res.getCreateTime());

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
        if (resourceData.containsKey("categoryId")) {
            resource.setCategoryId(Long.valueOf(resourceData.get("categoryId").toString()));
        }
        resource.setFileUrl((String) resourceData.get("fileUrl"));
        resource.setFileName((String) resourceData.get("fileName"));
        resource.setFileType((String) resourceData.get("fileType"));
        if (resourceData.containsKey("fileSize")) {
            resource.setFileSize(Long.valueOf(resourceData.get("fileSize").toString()));
        }
        resource.setTeacherId(teacherId);
        resource.setVisibility((String) resourceData.get("visibility"));
        if (resourceData.containsKey("courseId")) {
            resource.setCourseId(Long.valueOf(resourceData.get("courseId").toString()));
        }
        resource.setAuditStatus(AUDIT_PENDING);
        resource.setViewCount(0);
        resource.setDownloadCount(0);

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

        teachingResourceMapper.updateById(resource);
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

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalStudents", totalStudents);
        result.put("avgCompletionRate", avgCompletionRate);
        result.put("avgScore", avgScore);
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
    public List<Map<String, Object>> getAtRiskStudents(Long classId, String filterType) {
        Long teacherId = UserContext.getUserId();

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
            return new ArrayList<>();
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
                Map<String, Object> risk = new LinkedHashMap<>();
                risk.put("studentId", student.getId());
                risk.put("studentName", student.getRealName() != null ? student.getRealName() : student.getUsername());
                risk.put("className", student.getClassId() != null ? getClassName(student.getClassId()) : "");
                risk.put("avgScore", avgScore);
                risk.put("avgProgress", avgProgress);
                risk.put("missedTaskCount", missedTaskCount);
                risk.put("riskReason", riskReason);
                riskList.add(risk);
            }
        }

        return riskList;
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

        if ("HIGH".equalsIgnoreCase(importance)) {
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
            item.put("isTop", ann.getIsTop());
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

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("unreadCount", unreadCount);
        result.put("records", msgPageResult.getRecords());
        result.put("total", msgPageResult.getTotal());
        result.put("page", msgPageResult.getCurrent());
        result.put("pageSize", msgPageResult.getSize());

        return result;
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 根据班级 ID 获取班级名称
     */
    private String getClassName(Long classId) {
        SysClass sysClass = sysClassMapper.selectById(classId);
        return sysClass != null ? sysClass.getClassName() : "";
    }
}
