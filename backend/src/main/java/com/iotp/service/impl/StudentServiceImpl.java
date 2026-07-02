package com.iotp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iotp.common.BusinessException;
import com.iotp.entity.*;
import com.iotp.mapper.*;
import com.iotp.security.UserContext;
import com.iotp.service.StudentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 学生端业务服务实现
 * <p>字段命名规范对齐教师端，保证前后端统一。</p>
 */
@Service
public class StudentServiceImpl implements StudentService {

    private static final Logger log = LoggerFactory.getLogger(StudentServiceImpl.class);

    /** 提交状态常量 */
    private static final String SUBMIT_STATUS_SUBMITTED = "SUBMITTED";
    private static final String SUBMIT_STATUS_GRADED = "GRADED";
    private static final String SUBMIT_STATUS_RETURNED = "RETURNED";

    /** 审核状态常量 */
    private static final String AUDIT_STATUS_APPROVED = "APPROVED";

    /** 默认最大重交次数 */
    private static final int DEFAULT_MAX_RETRY_COUNT = 3;

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
    private TeachingResourceMapper teachingResourceMapper;

    @Autowired
    private ResourceCategoryMapper resourceCategoryMapper;

    @Autowired
    private StudentFavoriteResourceMapper favoriteResourceMapper;

    @Autowired
    private StudentGradeMapper studentGradeMapper;

    @Autowired
    private LearningAnalyticsDailyMapper analyticsDailyMapper;

    @Autowired
    private AcademicDiagnosisMapper academicDiagnosisMapper;

    @Autowired
    private AcademicDiagnosisRuleMapper academicDiagnosisRuleMapper;

    @Autowired
    private SysAnnouncementMapper sysAnnouncementMapper;

    @Autowired
    private SysMessageMapper sysMessageMapper;

    @Autowired
    private SysSemesterMapper sysSemesterMapper;

    @Autowired
    private SysClassMapper sysClassMapper;

    // ==================== 首页看板 ====================

    @Override
    public Map<String, Object> getDashboard() {
        Long studentId = UserContext.getUserId();

        // 1. 构建待办清单
        List<Map<String, Object>> todoList = new ArrayList<>();

        // 1.1 查询该学生未完成的课程章节 → 类型标记为 COURSE
        QueryWrapper<StudentCourseEnrollment> enrollQw = new QueryWrapper<>();
        enrollQw.eq("student_id", studentId)
                .eq("is_completed", 0);
        List<StudentCourseEnrollment> enrollments = enrollmentMapper.selectList(enrollQw);

        Set<Long> planIds = enrollments.stream()
                .map(StudentCourseEnrollment::getCoursePlanId)
                .collect(Collectors.toSet());

        if (!planIds.isEmpty()) {
            List<CoursePlan> plans = coursePlanMapper.selectBatchIds(planIds);
            Set<Long> courseIds = plans.stream()
                    .map(CoursePlan::getCourseId)
                    .collect(Collectors.toSet());

            if (!courseIds.isEmpty()) {
                for (Long courseId : courseIds) {
                    Course course = courseMapper.selectById(courseId);
                    String courseName = course != null ? course.getCourseName() : "";

                    QueryWrapper<CourseChapter> chQw = new QueryWrapper<>();
                    chQw.eq("course_id", courseId)
                            .orderByAsc("chapter_order");
                    List<CourseChapter> chapters = courseChapterMapper.selectList(chQw);

                    for (CourseChapter chapter : chapters) {
                        QueryWrapper<StudentLearningProgress> lpQw = new QueryWrapper<>();
                        lpQw.eq("student_id", studentId)
                                .eq("course_id", courseId)
                                .eq("chapter_id", chapter.getId());
                        StudentLearningProgress progress = learningProgressMapper.selectOne(lpQw);

                        boolean completed = progress != null
                                && Integer.valueOf(1).equals(progress.getIsCompleted());

                        if (!completed) {
                            Map<String, Object> todo = new LinkedHashMap<>();
                            todo.put("type", "COURSE");
                            todo.put("courseId", courseId);
                            todo.put("chapterId", chapter.getId());
                            todo.put("chapterName", chapter.getChapterName());
                            todo.put("courseName", courseName);
                            todo.put("title", "学习章节：" + chapter.getChapterName());
                            todo.put("linkUrl", "/courses/" + courseId);
                            todoList.add(todo);
                        }
                    }
                }
            }
        }

        // 1.2 查询未提交的实验任务 → 类型标记为 EXPERIMENT
        Long classId = getCurrentUserClassId();
        if (classId != null) {
            QueryWrapper<ExperimentTask> taskQw = new QueryWrapper<>();
            taskQw.eq("class_id", classId)
                    .eq("audit_status", AUDIT_STATUS_APPROVED);
            List<ExperimentTask> tasks = experimentTaskMapper.selectList(taskQw);

            for (ExperimentTask task : tasks) {
                QueryWrapper<StudentExperimentSubmission> subQw = new QueryWrapper<>();
                subQw.eq("task_id", task.getId())
                        .eq("student_id", studentId);
                StudentExperimentSubmission submission = submissionMapper.selectOne(subQw);

                boolean submitted = submission != null
                        && (SUBMIT_STATUS_SUBMITTED.equals(submission.getStatus())
                        || SUBMIT_STATUS_GRADED.equals(submission.getStatus()));

                if (!submitted) {
                    ExperimentProject project = experimentProjectMapper.selectById(task.getProjectId());
                    String projectName = project != null ? project.getProjectName() : "未知实验";

                    Map<String, Object> todo = new LinkedHashMap<>();
                    todo.put("type", "EXPERIMENT");
                    todo.put("taskId", task.getId());
                    todo.put("courseName", projectName);
                    todo.put("title", "提交实验：" + projectName);
                    todo.put("deadline", task.getEndTime());
                    todo.put("linkUrl", "/tasks");
                    todoList.add(todo);
                }
            }
        }

        // 2. 获取最近通知（公告 + 消息，取前 5 条）
        List<Map<String, Object>> notifications = new ArrayList<>();

        // 2.1 系统公告
        QueryWrapper<SysAnnouncement> annQw = new QueryWrapper<>();
        annQw.eq("status", 1)
                .orderByDesc("publish_time")
                .last("LIMIT 5");
        List<SysAnnouncement> announcements = sysAnnouncementMapper.selectList(annQw);
        for (SysAnnouncement ann : announcements) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", ann.getId());
            item.put("type", "ANNOUNCEMENT");
            item.put("title", ann.getTitle());
            item.put("content", ann.getContent());
            item.put("publisher", "");
            item.put("publishTime", ann.getPublishTime());
            notifications.add(item);
        }

        // 2.2 个人消息
        QueryWrapper<SysMessage> msgQw = new QueryWrapper<>();
        msgQw.eq("receiver_id", studentId)
                .orderByDesc("create_time")
                .last("LIMIT 5");
        List<SysMessage> messages = sysMessageMapper.selectList(msgQw);
        for (SysMessage msg : messages) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", msg.getId());
            item.put("type", "MESSAGE");
            item.put("title", msg.getTitle());
            item.put("content", msg.getContent());
            item.put("publisher", "");
            item.put("publishTime", msg.getCreateTime());
            notifications.add(item);
        }

        // 按时间排序，取前 5 条
        notifications.sort((a, b) -> {
            LocalDateTime ta = (LocalDateTime) a.get("publishTime");
            LocalDateTime tb = (LocalDateTime) b.get("publishTime");
            if (ta == null) ta = LocalDateTime.MIN;
            if (tb == null) tb = LocalDateTime.MIN;
            return tb.compareTo(ta);
        });
        if (notifications.size() > 5) {
            notifications = notifications.subList(0, 5);
        }

        // 3. 获取今日学习统计
        Map<String, Object> todayStats = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();

        // 3.1 今日学习时长
        QueryWrapper<LearningAnalyticsDaily> todayQw = new QueryWrapper<>();
        todayQw.eq("student_id", studentId)
                .eq("stat_date", today);
        List<LearningAnalyticsDaily> todayRecords = analyticsDailyMapper.selectList(todayQw);
        int todayDuration = todayRecords.stream()
                .mapToInt(r -> r.getStudyDurationMinutes() != null ? r.getStudyDurationMinutes() : 0)
                .sum();
        todayStats.put("studyDuration", todayDuration);

        // 3.2 本周完成课程数（统计有进度记录的课程数）
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        QueryWrapper<LearningAnalyticsDaily> weekQw = new QueryWrapper<>();
        weekQw.eq("student_id", studentId)
                .between("stat_date", weekStart, weekEnd);
        List<LearningAnalyticsDaily> weekRecords = analyticsDailyMapper.selectList(weekQw);
        Set<Long> weekCourseIds = weekRecords.stream()
                .map(LearningAnalyticsDaily::getCourseId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        todayStats.put("completedCourses", weekCourseIds.size());

        // 3.3 待完成任务数
        long pendingTasks = 0;
        if (classId != null) {
            QueryWrapper<ExperimentTask> pendingTaskQw = new QueryWrapper<>();
            pendingTaskQw.eq("class_id", classId)
                    .eq("audit_status", AUDIT_STATUS_APPROVED);
            List<ExperimentTask> allClassTasks = experimentTaskMapper.selectList(pendingTaskQw);
            for (ExperimentTask t : allClassTasks) {
                QueryWrapper<StudentExperimentSubmission> checkQw = new QueryWrapper<>();
                checkQw.eq("task_id", t.getId())
                        .eq("student_id", studentId);
                StudentExperimentSubmission sub = submissionMapper.selectOne(checkQw);
                if (sub == null || SUBMIT_STATUS_RETURNED.equals(sub.getStatus())) {
                    pendingTasks++;
                }
            }
        }
        todayStats.put("pendingTasks", (int) pendingTasks);

        // 4. 组装结果
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("todoList", todoList);
        result.put("notifications", notifications);
        result.put("todayStats", todayStats);

        return result;
    }

    // ==================== 课程学习 ====================

    @Override
    public IPage<Map<String, Object>> getCourses(String keyword, Long semester, Integer page, Integer pageSize) {
        Long studentId = UserContext.getUserId();
        Long classId = getCurrentUserClassId();

        if (classId == null) {
            return new Page<>(page, pageSize);
        }

        // 1. 分页查询 CoursePlan
        Page<CoursePlan> planPage = new Page<>(page, pageSize);
        QueryWrapper<CoursePlan> planQw = new QueryWrapper<>();
        planQw.eq("class_id", classId);
        if (semester != null) {
            planQw.eq("semester_id", semester);
        }
        planQw.orderByDesc("create_time");

        IPage<CoursePlan> planPageResult = coursePlanMapper.selectPage(planPage, planQw);
        List<CoursePlan> plans = planPageResult.getRecords();

        // 2. 组装课程数据
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

            // 查询教师姓名
            String teacherName = "";
            if (plan.getTeacherId() != null) {
                SysUser teacher = sysUserMapper.selectById(plan.getTeacherId());
                teacherName = teacher != null ? (teacher.getRealName() != null ? teacher.getRealName() : teacher.getUsername()) : "";
            }

            // 查询学期名称
            String semesterName = "";
            if (plan.getSemesterId() != null) {
                SysSemester sem = sysSemesterMapper.selectById(plan.getSemesterId());
                semesterName = sem != null ? sem.getSemesterName() : "";
            }

            // 查询选课记录获取进度
            QueryWrapper<StudentCourseEnrollment> enrollQw = new QueryWrapper<>();
            enrollQw.eq("student_id", studentId)
                    .eq("course_plan_id", plan.getId());
            StudentCourseEnrollment enrollment = enrollmentMapper.selectOne(enrollQw);

            // 从 scheduleInfo 解析课时信息
            int totalHours = 48;
            String startDate = "";
            if (plan.getScheduleInfo() != null) {
                try {
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    Map<String, Object> si = mapper.readValue(plan.getScheduleInfo(), Map.class);
                    if (si != null) {
                        if (si.get("totalHours") != null) totalHours = Integer.parseInt(si.get("totalHours").toString());
                        if (si.get("startDate") != null) startDate = si.get("startDate").toString();
                    }
                } catch (Exception ignored) {}
            }

            BigDecimal progressPercent = BigDecimal.ZERO;
            boolean completed = false;
            if (enrollment != null) {
                progressPercent = enrollment.getProgressPercent() != null
                        ? enrollment.getProgressPercent() : BigDecimal.ZERO;
                completed = Integer.valueOf(1).equals(enrollment.getIsCompleted());
            }
            int completedHours = (int) (totalHours * progressPercent.doubleValue() / 100);

            Map<String, Object> courseMap = new LinkedHashMap<>();
            courseMap.put("courseId", course.getId());
            courseMap.put("courseName", course.getCourseName());
            courseMap.put("courseCode", course.getCourseCode());
            courseMap.put("description", course.getDescription());
            courseMap.put("coverImage", course.getCoverImage());
            courseMap.put("planId", plan.getId());
            courseMap.put("semesterId", plan.getSemesterId());
            courseMap.put("semester", semesterName);
            courseMap.put("teacherId", plan.getTeacherId());
            courseMap.put("teacherName", teacherName);
            courseMap.put("progress", progressPercent.intValue());
            courseMap.put("completed", completed);
            courseMap.put("totalHours", totalHours);
            courseMap.put("completedHours", completedHours);
            courseMap.put("startDate", startDate);

            courseList.add(courseMap);
        }

        // 3. 构建分页结果
        Page<Map<String, Object>> resultPage = new Page<>(planPage.getCurrent(), planPage.getSize());
        resultPage.setTotal(planPageResult.getTotal());
        resultPage.setRecords(courseList);

        return resultPage;
    }

    @Override
    public Map<String, Object> getCourseDetail(Long courseId) {
        Long studentId = UserContext.getUserId();

        // 1. 查询课程
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new BusinessException(404, "课程不存在");
        }

        // 2. 查询 CoursePlan 获取教师和学期信息
        QueryWrapper<CoursePlan> planQw = new QueryWrapper<>();
        planQw.eq("course_id", courseId);
        CoursePlan plan = coursePlanMapper.selectOne(planQw);

        String teacherName = "";
        String semesterName = "";
        if (plan != null) {
            if (plan.getTeacherId() != null) {
                SysUser teacher = sysUserMapper.selectById(plan.getTeacherId());
                teacherName = teacher != null ? (teacher.getRealName() != null ? teacher.getRealName() : teacher.getUsername()) : "";
            }
            if (plan.getSemesterId() != null) {
                SysSemester sem = sysSemesterMapper.selectById(plan.getSemesterId());
                semesterName = sem != null ? sem.getSemesterName() : "";
            }
        }

        // 3. 查询所有章节
        QueryWrapper<CourseChapter> chapterQw = new QueryWrapper<>();
        chapterQw.eq("course_id", courseId)
                .orderByAsc("chapter_order");
        List<CourseChapter> chapters = courseChapterMapper.selectList(chapterQw);

        // 4. 查询每个章节的学习状态
        List<Map<String, Object>> chapterList = new ArrayList<>();
        int completedCount = 0;

        for (CourseChapter chapter : chapters) {
            QueryWrapper<StudentLearningProgress> lpQw = new QueryWrapper<>();
            lpQw.eq("student_id", studentId)
                    .eq("course_id", courseId)
                    .eq("chapter_id", chapter.getId());
            StudentLearningProgress progress = learningProgressMapper.selectOne(lpQw);

            String status;
            if (progress == null) {
                status = "NOT_STARTED";
            } else if (Integer.valueOf(1).equals(progress.getIsCompleted())) {
                status = "COMPLETED";
                completedCount++;
            } else {
                status = "IN_PROGRESS";
            }

            Map<String, Object> chapterMap = new LinkedHashMap<>();
            chapterMap.put("chapterId", chapter.getId());
            chapterMap.put("title", chapter.getChapterName());
            chapterMap.put("sortOrder", chapter.getChapterOrder());
            chapterMap.put("videoUrl", chapter.getVideoUrl());
            chapterMap.put("duration", chapter.getVideoDuration());
            chapterMap.put("contentText", chapter.getContentText());
            chapterMap.put("attachmentUrl", chapter.getAttachmentUrl());
            chapterMap.put("status", status);
            // materials 从 teaching_resource 表查询已审核的章节课件
            List<Map<String, Object>> materials = new ArrayList<>();
            QueryWrapper<TeachingResource> matQw = new QueryWrapper<>();
            matQw.eq("chapter_id", chapter.getId())
                    .eq("audit_status", "APPROVED")
                    .orderByAsc("create_time");
            List<TeachingResource> matList = teachingResourceMapper.selectList(matQw);
            for (TeachingResource mat : matList) {
                Map<String, Object> matMap = new LinkedHashMap<>();
                matMap.put("materialId", mat.getId());
                matMap.put("name", mat.getResourceName());
                matMap.put("fileUrl", mat.getFileUrl());
                matMap.put("fileName", mat.getFileName());
                matMap.put("fileType", mat.getFileType());
                matMap.put("fileSize", mat.getFileSize());
                materials.add(matMap);
            }
            chapterMap.put("materials", materials);

            if (progress != null) {
                chapterMap.put("watchedDuration", progress.getWatchedDuration());
                chapterMap.put("lastPosition", progress.getLastPosition());
            } else {
                chapterMap.put("watchedDuration", 0);
                chapterMap.put("lastPosition", 0);
            }

            chapterList.add(chapterMap);
        }

        // 5. 计算课程整体进度
        BigDecimal courseProgress = BigDecimal.ZERO;
        if (!chapters.isEmpty()) {
            courseProgress = BigDecimal.valueOf(completedCount)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(chapters.size()), 2, RoundingMode.HALF_UP);
        }

        // 6. 组装结果
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("courseId", course.getId());
        result.put("courseName", course.getCourseName());
        result.put("courseCode", course.getCourseCode());
        result.put("description", course.getDescription());
        result.put("coverImage", course.getCoverImage());
        result.put("teacherName", teacherName);
        result.put("semester", semesterName);
        result.put("progress", courseProgress.intValue());
        result.put("totalChapters", chapters.size());
        result.put("completedChapters", completedCount);
        result.put("chapters", chapterList);

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> updateProgress(Long courseId, Long chapterId, Integer position, Integer duration, Integer pageDuration) {
        Long studentId = UserContext.getUserId();

        // 1. 获取章节信息
        CourseChapter chapter = courseChapterMapper.selectById(chapterId);
        if (chapter == null) {
            throw new BusinessException(404, "章节不存在");
        }

        // 2. 查找或创建学习进度记录
        QueryWrapper<StudentLearningProgress> lpQw = new QueryWrapper<>();
        lpQw.eq("student_id", studentId)
                .eq("course_id", courseId)
                .eq("chapter_id", chapterId);
        StudentLearningProgress progress = learningProgressMapper.selectOne(lpQw);

        boolean isNew = false;
        if (progress == null) {
            progress = new StudentLearningProgress();
            progress.setStudentId(studentId);
            progress.setCourseId(courseId);
            progress.setChapterId(chapterId);
            progress.setWatchedDuration(0);
            progress.setLastPosition(0);
            progress.setIsCompleted(0);
            progress.setFirstWatchTime(LocalDateTime.now());
            isNew = true;
        }

        // 3. 更新观看时长和播放位置
        int newDuration = (progress.getWatchedDuration() != null ? progress.getWatchedDuration() : 0)
                + (duration != null ? duration : 0);
        progress.setWatchedDuration(newDuration);

        if (position != null) {
            progress.setLastPosition(position);
        }
        progress.setLastWatchTime(LocalDateTime.now());

        // 4. 判断是否完成
        int videoDuration = chapter.getVideoDuration() != null ? chapter.getVideoDuration() : 0;
        boolean chapterCompleted = false;
        if (videoDuration > 0 && position != null && position >= videoDuration) {
            progress.setIsCompleted(1);
            chapterCompleted = true;
        } else if (newDuration >= videoDuration && videoDuration > 0) {
            progress.setIsCompleted(1);
            chapterCompleted = true;
        }

        if (isNew) {
            learningProgressMapper.insert(progress);
        } else {
            learningProgressMapper.updateById(progress);
        }

        // 5. 重新计算课程进度百分比
        QueryWrapper<CourseChapter> allChQw = new QueryWrapper<>();
        allChQw.eq("course_id", courseId);
        long totalChapters = courseChapterMapper.selectCount(allChQw);

        QueryWrapper<StudentLearningProgress> completedLpQw = new QueryWrapper<>();
        completedLpQw.eq("student_id", studentId)
                .eq("course_id", courseId)
                .eq("is_completed", 1);
        long completedCount = learningProgressMapper.selectCount(completedLpQw);

        BigDecimal courseProgress = BigDecimal.ZERO;
        if (totalChapters > 0) {
            courseProgress = BigDecimal.valueOf(completedCount)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(totalChapters), 2, RoundingMode.HALF_UP);
        }

        // 6. 更新选课记录中的进度
        QueryWrapper<CoursePlan> planQw = new QueryWrapper<>();
        planQw.eq("course_id", courseId);
        CoursePlan plan = coursePlanMapper.selectOne(planQw);

        if (plan != null) {
            QueryWrapper<StudentCourseEnrollment> enrollQw = new QueryWrapper<>();
            enrollQw.eq("student_id", studentId)
                    .eq("course_plan_id", plan.getId());
            StudentCourseEnrollment enrollment = enrollmentMapper.selectOne(enrollQw);

            if (enrollment != null) {
                enrollment.setProgressPercent(courseProgress);
                if (completedCount >= totalChapters && totalChapters > 0) {
                    enrollment.setIsCompleted(1);
                }
                enrollmentMapper.updateById(enrollment);
            }
        }

        // 7. 更新今日学习时长统计（页面停留时间 + 视频观看时长）
        int totalSeconds = (pageDuration != null ? pageDuration : 0)
                + (duration != null ? duration : 0);
        if (totalSeconds > 0) {
            updateDailyStudyTime(studentId, courseId, totalSeconds);
        }

        // 8. 返回结果
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> chapterProgress = new LinkedHashMap<>();
        chapterProgress.put("chapterId", chapterId);
        chapterProgress.put("isCompleted", chapterCompleted);
        chapterProgress.put("watchedDuration", progress.getWatchedDuration());
        chapterProgress.put("lastPosition", progress.getLastPosition());
        result.put("chapterProgress", chapterProgress);
        result.put("courseProgress", courseProgress);

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> joinByInviteCode(String inviteCode) {
        Long studentId = UserContext.getUserId();

        // 1. 查找邀请码对应的课程计划
        QueryWrapper<CoursePlan> planQw = new QueryWrapper<>();
        planQw.eq("invite_code", inviteCode)
                .eq("invite_enabled", 1);
        CoursePlan plan = coursePlanMapper.selectOne(planQw);

        if (plan == null) {
            throw new BusinessException(400, "邀请码无效或已失效");
        }

        // 2. 检查是否过期
        if (plan.getInviteExpireTime() != null
                && plan.getInviteExpireTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(400, "邀请码已过期");
        }

        // 3. 检查是否已加入
        QueryWrapper<StudentCourseEnrollment> existQw = new QueryWrapper<>();
        existQw.eq("student_id", studentId)
                .eq("course_plan_id", plan.getId());
        if (enrollmentMapper.selectCount(existQw) > 0) {
            throw new BusinessException(400, "你已加入该课程，无需重复加入");
        }

        // 4. 插入选课记录
        StudentCourseEnrollment enrollment = new StudentCourseEnrollment();
        enrollment.setStudentId(studentId);
        enrollment.setCoursePlanId(plan.getId());
        enrollment.setEnrollSource("INVITE");
        enrollmentMapper.insert(enrollment);

        // 5. 获取课程信息
        Course course = courseMapper.selectById(plan.getCourseId());
        String courseName = course != null ? course.getCourseName() : "";

        log.info("学生 {} 通过邀请码 {} 加入了课程 {}", studentId, inviteCode, courseName);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("courseId", plan.getCourseId());
        result.put("courseName", courseName);
        result.put("planId", plan.getId());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> joinByInviteCode(String inviteCode) {
        Long studentId = UserContext.getUserId();

        // 1. 查找邀请码对应的课程计划
        QueryWrapper<CoursePlan> planQw = new QueryWrapper<>();
        planQw.eq("invite_code", inviteCode)
                .eq("invite_enabled", 1);
        CoursePlan plan = coursePlanMapper.selectOne(planQw);

        if (plan == null) {
            throw new BusinessException(400, "邀请码无效或已失效");
        }

        // 2. 检查是否过期
        if (plan.getInviteExpireTime() != null
                && plan.getInviteExpireTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(400, "邀请码已过期");
        }

        // 3. 检查是否已加入
        QueryWrapper<StudentCourseEnrollment> existQw = new QueryWrapper<>();
        existQw.eq("student_id", studentId)
                .eq("course_plan_id", plan.getId());
        if (enrollmentMapper.selectCount(existQw) > 0) {
            throw new BusinessException(400, "你已加入该课程，无需重复加入");
        }

        // 4. 插入选课记录
        StudentCourseEnrollment enrollment = new StudentCourseEnrollment();
        enrollment.setStudentId(studentId);
        enrollment.setCoursePlanId(plan.getId());
        enrollment.setEnrollSource("INVITE");
        enrollmentMapper.insert(enrollment);

        // 5. 获取课程信息
        Course course = courseMapper.selectById(plan.getCourseId());
        String courseName = course != null ? course.getCourseName() : "";

        log.info("学生 {} 通过邀请码 {} 加入了课程 {}", studentId, inviteCode, courseName);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("courseId", plan.getCourseId());
        result.put("courseName", courseName);
        result.put("planId", plan.getId());
        return result;
    }

    /**
     * 更新或创建今日学习分析记录
     */
    private void updateDailyStudyTime(Long studentId, Long courseId, Integer pageDurationSeconds) {
        LocalDate today = LocalDate.now();
        int addMinutes = Math.max(1, pageDurationSeconds / 60); // 最少记1分钟

        QueryWrapper<LearningAnalyticsDaily> qw = new QueryWrapper<>();
        qw.eq("student_id", studentId)
          .eq("stat_date", today)
          .eq("course_id", courseId);
        LearningAnalyticsDaily daily = analyticsDailyMapper.selectOne(qw);

        if (daily == null) {
            daily = new LearningAnalyticsDaily();
            daily.setStudentId(studentId);
            daily.setStatDate(today);
            daily.setCourseId(courseId);
            daily.setStudyDurationMinutes(addMinutes);
            daily.setExercisesCompleted(0);
            daily.setExercisesCorrect(0);
            daily.setTasksCompleted(0);
            analyticsDailyMapper.insert(daily);
        } else {
            daily.setStudyDurationMinutes(
                (daily.getStudyDurationMinutes() != null ? daily.getStudyDurationMinutes() : 0) + addMinutes
            );
            analyticsDailyMapper.updateById(daily);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> joinByInviteCode(String inviteCode) {
        Long studentId = UserContext.getUserId();

        // 1. 查找邀请码对应的课程计划
        QueryWrapper<CoursePlan> planQw = new QueryWrapper<>();
        planQw.eq("invite_code", inviteCode)
                .eq("invite_enabled", 1);
        CoursePlan plan = coursePlanMapper.selectOne(planQw);

        if (plan == null) {
            throw new BusinessException(400, "邀请码无效或已失效");
        }

        // 2. 检查是否过期
        if (plan.getInviteExpireTime() != null
                && plan.getInviteExpireTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(400, "邀请码已过期");
        }

        // 3. 检查是否已加入
        QueryWrapper<StudentCourseEnrollment> existQw = new QueryWrapper<>();
        existQw.eq("student_id", studentId)
                .eq("course_plan_id", plan.getId());
        if (enrollmentMapper.selectCount(existQw) > 0) {
            throw new BusinessException(400, "你已加入该课程，无需重复加入");
        }

        // 4. 插入选课记录
        StudentCourseEnrollment enrollment = new StudentCourseEnrollment();
        enrollment.setStudentId(studentId);
        enrollment.setCoursePlanId(plan.getId());
        enrollment.setEnrollSource("INVITE");
        enrollmentMapper.insert(enrollment);

        // 5. 获取课程信息
        Course course = courseMapper.selectById(plan.getCourseId());
        String courseName = course != null ? course.getCourseName() : "";

        log.info("学生 {} 通过邀请码 {} 加入了课程 {}", studentId, inviteCode, courseName);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("courseId", plan.getCourseId());
        result.put("courseName", courseName);
        result.put("planId", plan.getId());
        return result;
    }

    // ==================== 实验任务 ====================

    @Override
    public IPage<Map<String, Object>> getTasks(String type, String status, Integer page, Integer pageSize) {
        Long studentId = UserContext.getUserId();
        Long classId = getCurrentUserClassId();

        // 1. 分页查询实验任务
        Page<ExperimentTask> taskPage = new Page<>(page, pageSize);
        QueryWrapper<ExperimentTask> taskQw = new QueryWrapper<>();
        taskQw.eq("audit_status", AUDIT_STATUS_APPROVED);
        if (classId != null) {
            taskQw.eq("class_id", classId);
        }
        taskQw.orderByDesc("create_time");
        IPage<ExperimentTask> taskPageResult = experimentTaskMapper.selectPage(taskPage, taskQw);
        List<ExperimentTask> tasks = taskPageResult.getRecords();

        // 2. 组装数据
        List<Map<String, Object>> taskList = new ArrayList<>();
        for (ExperimentTask task : tasks) {
            ExperimentProject project = experimentProjectMapper.selectById(task.getProjectId());

            // 按项目类型过滤
            if (type != null && !type.isEmpty()) {
                if (project == null || !type.equals(project.getProjectType())) {
                    continue;
                }
            }

            // 获取学生提交信息
            QueryWrapper<StudentExperimentSubmission> subQw = new QueryWrapper<>();
            subQw.eq("task_id", task.getId())
                    .eq("student_id", studentId);
            StudentExperimentSubmission submission = submissionMapper.selectOne(subQw);

            // 确定学生端任务状态
            String studentStatus = determineStudentTaskStatus(task, submission);

            // 按学生端状态过滤
            if (status != null && !status.isEmpty()
                    && !status.equals(studentStatus)) {
                continue;
            }

            // 查询课程名称和教师名称
            String courseName = "";
            String teacherName = "";
            if (task.getTeacherId() != null) {
                SysUser teacher = sysUserMapper.selectById(task.getTeacherId());
                teacherName = teacher != null ? (teacher.getRealName() != null ? teacher.getRealName() : teacher.getUsername()) : "";
            }
            // 通过 project.course_id 直接获取课程
            if (project != null && project.getCourseId() != null) {
                Course relatedCourse = courseMapper.selectById(project.getCourseId());
                courseName = relatedCourse != null ? relatedCourse.getCourseName() : "";
            }

            Map<String, Object> taskMap = new LinkedHashMap<>();
            taskMap.put("taskId", task.getId());
            taskMap.put("projectId", task.getProjectId());
            taskMap.put("title", project != null ? project.getProjectName() : "");
            taskMap.put("taskType", project != null ? project.getProjectType() : "");
            taskMap.put("courseName", courseName);
            taskMap.put("teacherName", teacherName);
            taskMap.put("startTime", task.getStartTime());
            taskMap.put("endTime", task.getEndTime());
            taskMap.put("deadline", task.getEndTime());
            taskMap.put("status", studentStatus);
            taskMap.put("isOverdue", task.getEndTime() != null && task.getEndTime().isBefore(LocalDateTime.now()));

            if (submission != null) {
                taskMap.put("submissionId", submission.getId());
                taskMap.put("submitTime", submission.getSubmitTime());
                taskMap.put("score", submission.getScore());
                taskMap.put("resubmitCount", submission.getResubmitCount());
            }

            taskList.add(taskMap);
        }

        // 3. 构建分页结果
        Page<Map<String, Object>> resultPage = new Page<>(taskPage.getCurrent(), taskPage.getSize());
        resultPage.setTotal(taskPageResult.getTotal());
        resultPage.setRecords(taskList);

        return resultPage;
    }

    @Override
    public Map<String, Object> getTaskDetail(Long taskId) {
        Long studentId = UserContext.getUserId();

        // 1. 查询任务
        ExperimentTask task = experimentTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(404, "实验任务不存在");
        }

        // 2. 查询项目信息
        ExperimentProject project = experimentProjectMapper.selectById(task.getProjectId());

        // 3. 查询学生提交信息
        QueryWrapper<StudentExperimentSubmission> subQw = new QueryWrapper<>();
        subQw.eq("task_id", taskId)
                .eq("student_id", studentId);
        StudentExperimentSubmission submission = submissionMapper.selectOne(subQw);

        // 4. 查询课程名称和教师名称
        String courseName = "";
        String teacherName = "";
        if (task.getTeacherId() != null) {
            SysUser teacher = sysUserMapper.selectById(task.getTeacherId());
            teacherName = teacher != null ? (teacher.getRealName() != null ? teacher.getRealName() : teacher.getUsername()) : "";
        }
        // 通过 project.course_id 直接获取课程
        if (project != null && project.getCourseId() != null) {
            Course relatedCourse = courseMapper.selectById(project.getCourseId());
            courseName = relatedCourse != null ? relatedCourse.getCourseName() : "";
        }

        // 5. 构建指导文件列表（支持单 URL、URL 数组、{name,url} 对象数组）
        List<Map<String, Object>> guideFiles = new ArrayList<>();
        if (project != null && project.getGuideFileUrl() != null && !project.getGuideFileUrl().isEmpty()) {
            String raw = project.getGuideFileUrl();
            int idx = 1;
            if (raw.startsWith("[")) {
                try {
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    List<Object> items = mapper.readValue(raw, mapper.getTypeFactory().constructCollectionType(List.class, Object.class));
                    for (Object item : items) {
                        Map<String, Object> gf = new LinkedHashMap<>();
                        gf.put("fileId", idx++);
                        if (item instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> m = (Map<String, Object>) item;
                            Object urlObj = m.get("url");
                            String url = urlObj != null ? urlObj.toString() : "";
                            Object nameObj = m.get("name");
                            String name = nameObj != null ? nameObj.toString() : (url.contains("/") ? url.substring(url.lastIndexOf("/") + 1) : url);
                            gf.put("name", name);
                            gf.put("downloadUrl", url);
                        } else {
                            String url = String.valueOf(item);
                            String name = url.contains("/") ? url.substring(url.lastIndexOf("/") + 1) : url;
                            gf.put("name", name);
                            gf.put("downloadUrl", url);
                        }
                        gf.put("fileSize", 0);
                        guideFiles.add(gf);
                    }
                } catch (Exception e) {
                    Map<String, Object> gf = new LinkedHashMap<>();
                    gf.put("fileId", 1); gf.put("name", raw); gf.put("fileSize", 0); gf.put("downloadUrl", raw);
                    guideFiles.add(gf);
                }
            } else {
                Map<String, Object> gf = new LinkedHashMap<>();
                gf.put("fileId", 1);
                String name = raw.contains("/") ? raw.substring(raw.lastIndexOf("/") + 1) : raw;
                gf.put("name", name);
                gf.put("fileSize", 0);
                gf.put("downloadUrl", raw);
                guideFiles.add(gf);
            }
        }

        // 6. 组装结果（扁平结构，对齐教师端）
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", task.getId());
        result.put("title", project != null ? project.getProjectName() : "");
        result.put("taskType", project != null ? project.getProjectType() : "");
        result.put("description", project != null ? project.getDescription() : "");
        result.put("courseName", courseName);
        result.put("teacherName", teacherName);
        result.put("startTime", task.getStartTime());
        result.put("endTime", task.getEndTime());
        result.put("deadline", task.getEndTime());
        result.put("status", task.getStatus());
        result.put("isOverdue", task.getEndTime() != null
                && task.getEndTime().isBefore(LocalDateTime.now()));
        result.put("guideFiles", guideFiles);

        // 我的提交
        if (submission != null) {
            Map<String, Object> mySubmission = new LinkedHashMap<>();
            mySubmission.put("id", submission.getId());
            mySubmission.put("processDescription", submission.getProcessDescription());
            mySubmission.put("reportFileUrl", submission.getReportFileUrl());
            mySubmission.put("reportFileName", submission.getReportFileName());
            mySubmission.put("score", submission.getScore());
            mySubmission.put("comment", submission.getTeacherComment());
            mySubmission.put("status", submission.getStatus());
            mySubmission.put("submitTime", submission.getSubmitTime());
            mySubmission.put("gradeTime", submission.getGradeTime());
            mySubmission.put("resubmitCount", submission.getResubmitCount());
            result.put("mySubmission", mySubmission);
        }

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submitTask(Long taskId, String content, String reportFileUrl) {
        Long studentId = UserContext.getUserId();

        ExperimentTask task = experimentTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(404, "实验任务不存在");
        }

        if (task.getEndTime() != null && task.getEndTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(400, "任务已截止，无法提交");
        }

        QueryWrapper<StudentExperimentSubmission> existQw = new QueryWrapper<>();
        existQw.eq("task_id", taskId)
                .eq("student_id", studentId);
        StudentExperimentSubmission existing = submissionMapper.selectOne(existQw);
        if (existing != null) {
            throw new BusinessException(409, "已提交过该任务，请使用重新提交功能");
        }

        StudentExperimentSubmission submission = new StudentExperimentSubmission();
        submission.setTaskId(taskId);
        submission.setStudentId(studentId);
        submission.setProcessDescription(content);
        submission.setReportFileUrl(reportFileUrl);
        submission.setStatus(SUBMIT_STATUS_SUBMITTED);
        submission.setSubmitTime(LocalDateTime.now());
        submission.setResubmitCount(1);

        submissionMapper.insert(submission);

        log.info("学生 {} 提交了任务 {}", studentId, taskId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("submissionId", submission.getId());
        result.put("status", submission.getStatus());
        result.put("submitTime", submission.getSubmitTime());

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> resubmitTask(Long taskId, String content, String reportFileUrl) {
        Long studentId = UserContext.getUserId();

        ExperimentTask task = experimentTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(404, "实验任务不存在");
        }

        QueryWrapper<StudentExperimentSubmission> existQw = new QueryWrapper<>();
        existQw.eq("task_id", taskId)
                .eq("student_id", studentId);
        StudentExperimentSubmission submission = submissionMapper.selectOne(existQw);

        if (submission == null) {
            throw new BusinessException(400, "请先提交后再重新提交");
        }

        if (!SUBMIT_STATUS_RETURNED.equals(submission.getStatus())) {
            throw new BusinessException(400, "当前状态不允许重新提交，仅在被退回时可重新提交");
        }

        // 被退回允许重交，即使已逾期（教师退回即表示允许补交）
        if (task.getEndTime() != null && task.getEndTime().isBefore(LocalDateTime.now())) {
            log.info("学生 {} 在截止时间后重新提交任务 {}（状态：已退回）", studentId, taskId);
        }

        int retryCount = submission.getResubmitCount() != null ? submission.getResubmitCount() : 0;
        if (retryCount >= DEFAULT_MAX_RETRY_COUNT) {
            throw new BusinessException(400, "已达到最大重交次数（" + DEFAULT_MAX_RETRY_COUNT + "次），无法继续重交");
        }

        submission.setProcessDescription(content);
        submission.setReportFileUrl(reportFileUrl);
        submission.setStatus(SUBMIT_STATUS_SUBMITTED);
        submission.setSubmitTime(LocalDateTime.now());
        submission.setResubmitCount(retryCount + 1);
        submission.setScore(null);
        submission.setTeacherComment(null);
        submission.setGradeTime(null);

        submissionMapper.updateById(submission);

        log.info("学生 {} 重新提交了任务 {}（第 {} 次重交）", studentId, taskId, retryCount + 1);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("submissionId", submission.getId());
        result.put("status", submission.getStatus());
        result.put("submitTime", submission.getSubmitTime());
        result.put("resubmitCount", submission.getResubmitCount());

        return result;
    }

    @Override
    public Map<String, Object> getTaskResult(Long taskId) {
        Long studentId = UserContext.getUserId();

        QueryWrapper<StudentExperimentSubmission> subQw = new QueryWrapper<>();
        subQw.eq("task_id", taskId)
                .eq("student_id", studentId);
        StudentExperimentSubmission submission = submissionMapper.selectOne(subQw);

        if (submission == null) {
            throw new BusinessException(404, "尚未提交该任务");
        }

        // 查询教师姓名
        ExperimentTask task = experimentTaskMapper.selectById(taskId);
        String teacherName = "";
        if (task != null && task.getTeacherId() != null) {
            SysUser teacher = sysUserMapper.selectById(task.getTeacherId());
            teacherName = teacher != null ? (teacher.getRealName() != null ? teacher.getRealName() : teacher.getUsername()) : "";
        }

        boolean canResubmit = SUBMIT_STATUS_RETURNED.equals(submission.getStatus())
                && (submission.getResubmitCount() == null
                || submission.getResubmitCount() < DEFAULT_MAX_RETRY_COUNT);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("submissionId", submission.getId());
        result.put("score", submission.getScore());
        result.put("comment", submission.getTeacherComment());
        result.put("teacherName", teacherName);
        result.put("status", submission.getStatus());
        result.put("submitTime", submission.getSubmitTime());
        result.put("gradedTime", submission.getGradeTime());
        result.put("gradeTime", submission.getGradeTime());
        result.put("canResubmit", canResubmit);
        result.put("resubmitCount", submission.getResubmitCount());

        return result;
    }

    // ==================== 教学资源 ====================

    @Override
    public IPage<Map<String, Object>> getResources(String keyword, Long category, Long courseId,
                                                   String scope, Integer page, Integer pageSize) {
        Long studentId = UserContext.getUserId();

        // 1. 分页查询
        Page<TeachingResource> resPage = new Page<>(page, pageSize);
        QueryWrapper<TeachingResource> resQw = new QueryWrapper<>();
        resQw.eq("audit_status", AUDIT_STATUS_APPROVED);

        if (keyword != null && !keyword.isEmpty()) {
            resQw.like("resource_name", keyword);
        }
        if (category != null) {
            resQw.eq("category_id", category);
        }
        if (courseId != null) {
            resQw.eq("course_id", courseId);
        }
        // scope 处理：FAVORITE 表示只看收藏，否则按可见范围过滤
        boolean onlyFavorites = "FAVORITE".equals(scope);
        if (!onlyFavorites && scope != null && !scope.isEmpty()) {
            resQw.eq("visibility", scope);
        }
        resQw.orderByDesc("create_time");

        IPage<TeachingResource> resPageResult = teachingResourceMapper.selectPage(resPage, resQw);
        List<TeachingResource> resources = resPageResult.getRecords();

        // 2. 查询当前学生的收藏列表
        QueryWrapper<StudentFavoriteResource> favQw = new QueryWrapper<>();
        favQw.eq("student_id", studentId);
        List<StudentFavoriteResource> favList = favoriteResourceMapper.selectList(favQw);
        Set<Long> favoritedIds = favList.stream()
                .map(StudentFavoriteResource::getResourceId)
                .collect(Collectors.toSet());

        // 3. 组装数据（如果只查收藏，则过滤）
        List<Map<String, Object>> resourceList = new ArrayList<>();
        for (TeachingResource res : resources) {
            // FAVORITE 模式：跳过未收藏的
            if (onlyFavorites && !favoritedIds.contains(res.getId())) {
                continue;
            }
            // 查询分类名称
            String categoryName = "";
            String type = "OTHER";
            if (res.getCategoryId() != null) {
                ResourceCategory cat = resourceCategoryMapper.selectById(res.getCategoryId());
                if (cat != null) {
                    categoryName = cat.getCategoryName();
                    // 根据分类名推导 type
                    if (categoryName.contains("课件")) type = "COURSEWARE";
                    else if (categoryName.contains("习题")) type = "EXERCISE";
                    else if (categoryName.contains("视频")) type = "VIDEO";
                    else if (categoryName.contains("文档")) type = "DOCUMENT";
                }
            }

            // 查询课程名称
            String courseName = "";
            if (res.getCourseId() != null) {
                Course c = courseMapper.selectById(res.getCourseId());
                courseName = c != null ? c.getCourseName() : "";
            }
            if (courseName.isEmpty()) courseName = "公共资源";

            // 查询教师姓名
            String teacherName = "";
            if (res.getTeacherId() != null) {
                SysUser teacher = sysUserMapper.selectById(res.getTeacherId());
                teacherName = teacher != null ? (teacher.getRealName() != null ? teacher.getRealName() : teacher.getUsername()) : "";
            }

            Map<String, Object> resMap = new LinkedHashMap<>();
            resMap.put("resourceId", res.getId());
            resMap.put("name", res.getResourceName());
            resMap.put("resourceName", res.getResourceName());
            resMap.put("description", res.getDescription());
            resMap.put("categoryId", res.getCategoryId());
            resMap.put("categoryName", categoryName);
            resMap.put("type", type);
            resMap.put("fileUrl", res.getFileUrl());
            resMap.put("fileName", res.getFileName());
            resMap.put("fileType", res.getFileType());
            resMap.put("fileSize", res.getFileSize());
            resMap.put("teacherId", res.getTeacherId());
            resMap.put("teacherName", teacherName);
            resMap.put("visibility", res.getVisibility());
            resMap.put("courseId", res.getCourseId());
            resMap.put("courseName", courseName);
            resMap.put("viewCount", res.getViewCount());
            resMap.put("downloadCount", res.getDownloadCount());
            resMap.put("createTime", res.getCreateTime());
            resMap.put("uploadTime", res.getCreateTime());
            resMap.put("isFavorited", favoritedIds.contains(res.getId()));
            // 预览 URL（暂无专门字段，用文件 URL）
            resMap.put("previewUrl", res.getFileUrl());

            resourceList.add(resMap);
        }

        // 4. 构建分页结果
        Page<Map<String, Object>> resultPage = new Page<>(resPage.getCurrent(), resPage.getSize());
        resultPage.setTotal(resPageResult.getTotal());
        resultPage.setRecords(resourceList);

        return resultPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> favoriteResource(Long resourceId) {
        Long studentId = UserContext.getUserId();

        TeachingResource resource = teachingResourceMapper.selectById(resourceId);
        if (resource == null) {
            throw new BusinessException(404, "资源不存在");
        }

        QueryWrapper<StudentFavoriteResource> favQw = new QueryWrapper<>();
        favQw.eq("student_id", studentId)
                .eq("resource_id", resourceId);
        StudentFavoriteResource fav = favoriteResourceMapper.selectOne(favQw);

        boolean isFavorited;
        if (fav == null) {
            StudentFavoriteResource newFav = new StudentFavoriteResource();
            newFav.setStudentId(studentId);
            newFav.setResourceId(resourceId);
            favoriteResourceMapper.insert(newFav);
            isFavorited = true;
            log.info("学生 {} 收藏了资源 {}", studentId, resourceId);
        } else {
            favoriteResourceMapper.deleteById(fav.getId());
            isFavorited = false;
            log.info("学生 {} 取消了资源 {} 的收藏", studentId, resourceId);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("resourceId", resourceId);
        result.put("isFavorited", isFavorited);

        return result;
    }

    @Override
    public Object[] downloadResource(Long resourceId) {
        TeachingResource resource = teachingResourceMapper.selectById(resourceId);
        if (resource == null) {
            throw new BusinessException(404, "资源不存在");
        }

        // 从 fileUrl 解析本地文件路径
        // 支持两种格式：
        // 1. 旧格式：/api/v1/common/files/xxx/yyy/zzz.ext
        // 2. 新格式：/api/v1/common/file?path=xxx%2Fyyy%2Fzzz.ext
        String fileUrl = resource.getFileUrl();
        if (fileUrl == null || fileUrl.isEmpty()) {
            throw new BusinessException(400, "资源文件不存在");
        }

        String relativePath;
        if (fileUrl.contains("?path=")) {
            // 新格式：从查询参数中提取路径
            String encoded = fileUrl.substring(fileUrl.indexOf("?path=") + 6);
            try {
                relativePath = java.net.URLDecoder.decode(encoded, "UTF-8");
            } catch (Exception e) {
                relativePath = encoded;
            }
        } else if (fileUrl.startsWith("/api/v1/common/files/")) {
            // 旧格式
            relativePath = fileUrl.substring("/api/v1/common/files/".length());
        } else if (fileUrl.startsWith("/api/v1/common/file/")) {
            relativePath = fileUrl.substring("/api/v1/common/file/".length());
        } else {
            relativePath = fileUrl.startsWith("/") ? fileUrl.substring(1) : fileUrl;
        }

        Path filePath = Paths.get("uploads", relativePath);
        if (!Files.exists(filePath) || !Files.isReadable(filePath)) {
            throw new BusinessException(404, "文件未找到或不可读");
        }

        try {
            byte[] fileBytes = Files.readAllBytes(filePath);
            String fileName = resource.getFileName();
            if (fileName == null || fileName.isEmpty()) {
                fileName = filePath.getFileName().toString();
            }

            // 更新下载次数
            int currentCount = resource.getDownloadCount() != null ? resource.getDownloadCount() : 0;
            resource.setDownloadCount(currentCount + 1);
            teachingResourceMapper.updateById(resource);

            log.info("学生 {} 下载了资源 {}：{}（下载次数：{}）", UserContext.getUserId(), resourceId, fileName, currentCount + 1);
            return new Object[]{fileName, fileBytes};
        } catch (IOException e) {
            log.error("读取资源文件失败：{}", e.getMessage(), e);
            throw new BusinessException(500, "文件读取失败");
        }
    }

    @Override
    public Object[] downloadMaterial(Long courseId, Long chapterId) {
        CourseChapter chapter = courseChapterMapper.selectById(chapterId);
        if (chapter == null || !courseId.equals(chapter.getCourseId())) {
            throw new BusinessException(404, "章节或资料不存在");
        }
        String attachmentUrl = chapter.getAttachmentUrl();
        if (attachmentUrl == null || attachmentUrl.isEmpty()) {
            throw new BusinessException(400, "该章节暂无资料附件");
        }

        // attachmentUrl 格式：/api/v1/common/files/courseware/xxx.pdf
        String prefix = "/api/v1/common/files/";
        String relativePath = attachmentUrl.startsWith(prefix)
                ? attachmentUrl.substring(prefix.length())
                : attachmentUrl;
        Path filePath = Paths.get("uploads", relativePath);
        if (!Files.exists(filePath) || !Files.isReadable(filePath)) {
            throw new BusinessException(404, "文件未找到或不可读");
        }

        try {
            byte[] fileBytes = Files.readAllBytes(filePath);
            String fileName = filePath.getFileName().toString();
            return new Object[]{fileName, fileBytes};
        } catch (IOException e) {
            throw new BusinessException(500, "文件读取失败");
        }
    }

    // ==================== 学情分析 ====================

    @Override
    public Map<String, Object> getAnalyticsOverview(Long semester) {
        Long studentId = UserContext.getUserId();

        // 1. 总学习时长（分钟）
        QueryWrapper<LearningAnalyticsDaily> durQw = new QueryWrapper<>();
        durQw.eq("student_id", studentId);
        if (semester != null) {
            SysSemester sem = sysSemesterMapper.selectById(semester);
            if (sem != null && sem.getStartDate() != null && sem.getEndDate() != null) {
                durQw.between("stat_date", sem.getStartDate(), sem.getEndDate());
            }
        }
        List<LearningAnalyticsDaily> allDaily = analyticsDailyMapper.selectList(durQw);
        int totalStudyMinutes = allDaily.stream()
                .mapToInt(r -> r.getStudyDurationMinutes() != null ? r.getStudyDurationMinutes() : 0)
                .sum();

        // 2. 平均分
        QueryWrapper<StudentGrade> gradeQw = new QueryWrapper<>();
        gradeQw.eq("student_id", studentId)
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

        // 3. 任务完成率
        int totalTasks = 0;
        int completedTasks = 0;
        QueryWrapper<ExperimentTask> taskQw = new QueryWrapper<>();
        taskQw.eq("audit_status", AUDIT_STATUS_APPROVED);
        List<ExperimentTask> allTasks = experimentTaskMapper.selectList(taskQw);
        for (ExperimentTask task : allTasks) {
            QueryWrapper<StudentExperimentSubmission> subQw = new QueryWrapper<>();
            subQw.eq("task_id", task.getId())
                    .eq("student_id", studentId);
            StudentExperimentSubmission sub = submissionMapper.selectOne(subQw);
            if (sub != null && (SUBMIT_STATUS_SUBMITTED.equals(sub.getStatus())
                    || SUBMIT_STATUS_GRADED.equals(sub.getStatus()))) {
                completedTasks++;
            }
            totalTasks++;
        }
        BigDecimal taskCompletionRate = BigDecimal.ZERO;
        if (totalTasks > 0) {
            taskCompletionRate = BigDecimal.valueOf(completedTasks)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(totalTasks), 2, RoundingMode.HALF_UP);
        }

        // 4.0 提前查询已批改的提交记录（用于各科实验/实训均分和整体均分）
        QueryWrapper<StudentExperimentSubmission> avgSubQw = new QueryWrapper<>();
        avgSubQw.eq("student_id", studentId)
                .eq("status", SUBMIT_STATUS_GRADED)
                .isNotNull("score");
        List<StudentExperimentSubmission> allGradedSubs = submissionMapper.selectList(avgSubQw);

        // 4. 各科统计（按成绩分组，有成绩的课程都显示）
        List<Map<String, Object>> courseStats = new ArrayList<>();
        // 从成绩表按课程分组
        Map<Long, List<StudentGrade>> gradesByCourse = new LinkedHashMap<>();
        for (StudentGrade g : grades) {
            if (g.getCoursePlanId() != null) {
                CoursePlan cp = coursePlanMapper.selectById(g.getCoursePlanId());
                if (cp != null && cp.getCourseId() != null) {
                    gradesByCourse.computeIfAbsent(cp.getCourseId(), k -> new ArrayList<>()).add(g);
                }
            }
        }

        // 从提交记录按课程汇总实验/实训得分（直接通过 project.courseId）
        Map<Long, List<BigDecimal>> subExpScoresByCourse = new LinkedHashMap<>();
        Map<Long, List<BigDecimal>> subTrainScoresByCourse = new LinkedHashMap<>();
        for (StudentExperimentSubmission sub : allGradedSubs) {
            ExperimentTask task = experimentTaskMapper.selectById(sub.getTaskId());
            if (task == null) continue;
            ExperimentProject project = experimentProjectMapper.selectById(task.getProjectId());
            if (project == null || project.getCourseId() == null) continue;
            Long courseId = project.getCourseId();
            String pType = project.getProjectType();
            if ("EXPERIMENT".equals(pType)) {
                subExpScoresByCourse.computeIfAbsent(courseId, k -> new ArrayList<>()).add(sub.getScore());
            } else if ("TRAINING".equals(pType)) {
                subTrainScoresByCourse.computeIfAbsent(courseId, k -> new ArrayList<>()).add(sub.getScore());
            }
        }

        // 合并所有有数据来源的课程：成绩 + 实验提交 + 实训提交 + 选课记录
        Set<Long> allCourseIds = new LinkedHashSet<>();
        allCourseIds.addAll(gradesByCourse.keySet());
        allCourseIds.addAll(subExpScoresByCourse.keySet());
        allCourseIds.addAll(subTrainScoresByCourse.keySet());
        // 学生选课记录
        QueryWrapper<StudentCourseEnrollment> enrollStatsQw = new QueryWrapper<>();
        enrollStatsQw.eq("student_id", studentId);
        List<StudentCourseEnrollment> allEnrollments = enrollmentMapper.selectList(enrollStatsQw);
        for (StudentCourseEnrollment e : allEnrollments) {
            if (e.getCoursePlanId() != null) {
                CoursePlan cp = coursePlanMapper.selectById(e.getCoursePlanId());
                if (cp != null && cp.getCourseId() != null) {
                    allCourseIds.add(cp.getCourseId());
                }
            }
        }

        for (Long cid : allCourseIds) {
            Course course = courseMapper.selectById(cid);
            if (course == null) continue;

            // 该课程的学习时长（从 daily 表汇总，可能为 0）
            int courseMinutes = allDaily.stream()
                    .filter(d -> cid.equals(d.getCourseId()))
                    .mapToInt(d -> d.getStudyDurationMinutes() != null ? d.getStudyDurationMinutes() : 0)
                    .sum();

            // 该课程的总评成绩（多份取平均）
            BigDecimal courseScore = BigDecimal.ZERO;
            List<StudentGrade> courseGrades = gradesByCourse.getOrDefault(cid, Collections.emptyList());
            int finalCount = 0;
            for (StudentGrade g : courseGrades) {
                if (g.getFinalGrade() != null) {
                    courseScore = courseScore.add(g.getFinalGrade());
                    finalCount++;
                }
            }
            if (finalCount > 1) courseScore = courseScore.divide(BigDecimal.valueOf(finalCount), 2, RoundingMode.HALF_UP);

            // 实验均分（从提交记录按课程汇总）
            BigDecimal experimentScore = avgList(subExpScoresByCourse.get(cid));
            // 实训均分（从提交记录按课程汇总）
            BigDecimal trainingScore = avgList(subTrainScoresByCourse.get(cid));

            Map<String, Object> cs = new LinkedHashMap<>();
            cs.put("courseId", cid);
            cs.put("courseName", course.getCourseName());
            cs.put("studyDuration", courseMinutes);
            cs.put("studyMinutes", courseMinutes);
            cs.put("score", courseScore);
            cs.put("tasksCompleted", 0);
            cs.put("experimentScore", experimentScore);
            cs.put("trainingScore", trainingScore);
            cs.put("exerciseCorrectRate", BigDecimal.ZERO);
            cs.put("taskCompletionRate", taskCompletionRate);
            courseStats.add(cs);
        }

        // 5. 近 4 周学习趋势
        List<Map<String, Object>> weeklyTrend = new ArrayList<>();
        LocalDate today = LocalDate.now();
        DateTimeFormatter weekFmt = DateTimeFormatter.ofPattern("MM/dd");
        for (int i = 3; i >= 0; i--) {
            LocalDate weekStart = today.minusWeeks(i)
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            LocalDate weekEnd = weekStart.plusDays(6);

            // 学习时长
            QueryWrapper<LearningAnalyticsDaily> weekQw = new QueryWrapper<>();
            weekQw.eq("student_id", studentId)
                    .between("stat_date", weekStart, weekEnd);
            List<LearningAnalyticsDaily> weekData = analyticsDailyMapper.selectList(weekQw);
            int weekMin = weekData.stream()
                    .mapToInt(d -> d.getStudyDurationMinutes() != null ? d.getStudyDurationMinutes() : 0)
                    .sum();

            // 完成任务数（从提交记录统计）
            QueryWrapper<StudentExperimentSubmission> weekSubQw = new QueryWrapper<>();
            weekSubQw.eq("student_id", studentId)
                    .between("submit_time", weekStart.atStartOfDay(), weekEnd.plusDays(1).atStartOfDay());
            long weekTasks = submissionMapper.selectCount(weekSubQw);

            Map<String, Object> week = new LinkedHashMap<>();
            week.put("week", weekStart.format(weekFmt) + "-" + weekEnd.format(weekFmt));
            week.put("weekStart", weekStart);
            week.put("weekEnd", weekEnd);
            week.put("duration", weekMin);
            week.put("studyMinutes", weekMin);
            week.put("taskCount", (int) weekTasks);
            week.put("tasksCompleted", (int) weekTasks);
            weeklyTrend.add(week);
        }

        // 6. 实验均分 & 实训均分（从已批改的实验实训提交记录取平均）
        BigDecimal avgExperimentScore = BigDecimal.ZERO;
        BigDecimal avgTrainingScore = BigDecimal.ZERO;
        int expScoreCount = 0, trainScoreCount = 0;
        for (StudentExperimentSubmission sub : allGradedSubs) {
            ExperimentTask task = experimentTaskMapper.selectById(sub.getTaskId());
            if (task == null) continue;
            ExperimentProject project = experimentProjectMapper.selectById(task.getProjectId());
            if (project == null) continue;
            String pType = project.getProjectType();
            if ("EXPERIMENT".equals(pType)) {
                avgExperimentScore = avgExperimentScore.add(sub.getScore());
                expScoreCount++;
            } else if ("TRAINING".equals(pType)) {
                avgTrainingScore = avgTrainingScore.add(sub.getScore());
                trainScoreCount++;
            }
        }
        if (expScoreCount > 0) avgExperimentScore = avgExperimentScore.divide(BigDecimal.valueOf(expScoreCount), 2, RoundingMode.HALF_UP);
        if (trainScoreCount > 0) avgTrainingScore = avgTrainingScore.divide(BigDecimal.valueOf(trainScoreCount), 2, RoundingMode.HALF_UP);

        // 7. 组装结果
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalStudyDuration", totalStudyMinutes);
        result.put("totalStudyMinutes", totalStudyMinutes);
        result.put("avgScore", avgScore);
        result.put("avgExperimentScore", avgExperimentScore);
        result.put("avgTrainingScore", avgTrainingScore);
        result.put("taskCompletionRate", taskCompletionRate);
        result.put("completedTasks", completedTasks);
        result.put("totalTasks", totalTasks);
        result.put("courseStats", courseStats);
        result.put("weeklyTrend", weeklyTrend);

        return result;
    }

    @Override
    public Map<String, Object> getDiagnosis(Long semester) {
        Long studentId = UserContext.getUserId();

        // 1. 先尝试获取已有的诊断记录
        QueryWrapper<AcademicDiagnosis> diagQw = new QueryWrapper<>();
        diagQw.eq("student_id", studentId);
        if (semester != null) {
            diagQw.eq("semester_id", semester);
        }
        diagQw.orderByDesc("generated_time")
                .last("LIMIT 1");
        AcademicDiagnosis diagnosis = academicDiagnosisMapper.selectOne(diagQw);

        if (diagnosis != null) {
            return buildDiagnosisMap(diagnosis);
        }

        // 2. 没有诊断记录，根据成绩计算
        QueryWrapper<StudentGrade> gradeQw = new QueryWrapper<>();
        gradeQw.eq("student_id", studentId)
                .eq("is_published", 1);
        if (semester != null) {
            gradeQw.eq("semester_id", semester);
        }
        List<StudentGrade> grades = studentGradeMapper.selectList(gradeQw);

        BigDecimal avgFinalGrade = BigDecimal.ZERO;
        if (!grades.isEmpty()) {
            BigDecimal sum = grades.stream()
                    .filter(g -> g.getFinalGrade() != null)
                    .map(StudentGrade::getFinalGrade)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            long count = grades.stream().filter(g -> g.getFinalGrade() != null).count();
            if (count > 0) {
                avgFinalGrade = sum.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
            }
        }

        // 学习时长
        QueryWrapper<LearningAnalyticsDaily> durQw = new QueryWrapper<>();
        durQw.eq("student_id", studentId);
        if (semester != null) {
            SysSemester sem = sysSemesterMapper.selectById(semester);
            if (sem != null && sem.getStartDate() != null && sem.getEndDate() != null) {
                durQw.between("stat_date", sem.getStartDate(), sem.getEndDate());
            }
        }
        List<LearningAnalyticsDaily> dailyList = analyticsDailyMapper.selectList(durQw);
        int totalMinutes = dailyList.stream()
                .mapToInt(d -> d.getStudyDurationMinutes() != null ? d.getStudyDurationMinutes() : 0)
                .sum();

        // 计算诊断等级
        String academicLevel;
        String summary;
        String weakPointsStr;
        String suggestionsStr;

        if (avgFinalGrade.compareTo(BigDecimal.valueOf(90)) >= 0) {
            academicLevel = "EXCELLENT";
            summary = "学习成绩优异，继续保持。";
            weakPointsStr = "无明显薄弱点";
            suggestionsStr = "建议挑战更高难度的内容，拓展知识面。";
        } else if (avgFinalGrade.compareTo(BigDecimal.valueOf(80)) >= 0) {
            academicLevel = "GOOD";
            summary = "学习成绩良好，有提升空间。";
            weakPointsStr = "部分知识点掌握不够深入";
            suggestionsStr = "建议针对薄弱环节加强练习，多查阅相关资料。";
        } else if (avgFinalGrade.compareTo(BigDecimal.valueOf(70)) >= 0) {
            academicLevel = "NEED_IMPROVE";
            summary = "学习成绩中等，需加强学习。";
            weakPointsStr = "基础知识点存在漏洞";
            suggestionsStr = "建议回顾课程视频和教材，完成课后练习巩固知识。";
        } else if (avgFinalGrade.compareTo(BigDecimal.valueOf(60)) >= 0) {
            academicLevel = "NEED_IMPROVE";
            summary = "学习成绩偏低，需加倍努力。";
            weakPointsStr = "基础知识掌握不牢固";
            suggestionsStr = "建议制定学习计划，从基础开始复习，多向老师和同学请教。";
        } else {
            academicLevel = "NEED_IMPROVE";
            summary = "学习成绩不理想，需要重点关注。";
            weakPointsStr = "多个知识点掌握不足";
            suggestionsStr = "建议与任课老师沟通，制定个性化补习计划，利用课余时间加强学习。";
        }

        if (totalMinutes < 300) {
            suggestionsStr += "建议增加学习时间，保证每天至少 1-2 小时的学习时长。";
        }

        // 构造 weakPoints 数组
        List<Map<String, Object>> weakPoints = new ArrayList<>();
        Map<String, Object> wp = new LinkedHashMap<>();
        wp.put("knowledge", weakPointsStr);
        wp.put("courseName", "综合");
        weakPoints.add(wp);

        // 构造 suggestResources 数组
        List<Map<String, Object>> suggestResources = new ArrayList<>();
        Map<String, Object> sr = new LinkedHashMap<>();
        sr.put("resourceId", 0);
        sr.put("name", "课程视频回顾");
        sr.put("type", "VIDEO");
        suggestResources.add(sr);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("academicLevel", academicLevel);
        result.put("level", academicLevel);
        result.put("overallScore", avgFinalGrade);
        result.put("avgScore", avgFinalGrade);
        result.put("summary", summary);
        result.put("diagnosisReport", summary);
        result.put("weakPoints", weakPoints);
        result.put("suggestResources", suggestResources);
        result.put("suggestions", suggestionsStr);
        result.put("totalStudyMinutes", totalMinutes);
        result.put("totalStudyHours", totalMinutes / 60.0);
        result.put("courseCount", grades.size());

        return result;
    }

    // ==================== 个人信息 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProfile(Long userId, Map<String, Object> updates) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }

        if (updates.containsKey("avatar")) {
            user.setAvatar((String) updates.get("avatar"));
        }
        if (updates.containsKey("realName")) {
            user.setRealName((String) updates.get("realName"));
        }
        if (updates.containsKey("nickname")) {
            user.setRealName((String) updates.get("nickname"));
        }
        if (updates.containsKey("phone")) {
            user.setPhone((String) updates.get("phone"));
        }
        if (updates.containsKey("email")) {
            user.setEmail((String) updates.get("email"));
        }

        sysUserMapper.updateById(user);
        log.info("用户 {} 更新了个人资料", userId);
    }

    // ==================== 成绩查询 ====================

    @Override
    public IPage<Map<String, Object>> getGrades(Long semester, Integer page, Integer pageSize) {
        Long studentId = UserContext.getUserId();

        Page<StudentGrade> gradePage = new Page<>(page, pageSize);
        QueryWrapper<StudentGrade> gradeQw = new QueryWrapper<>();
        gradeQw.eq("student_id", studentId)
                .eq("is_published", 1);
        if (semester != null) {
            gradeQw.eq("semester_id", semester);
        }
        gradeQw.orderByDesc("create_time");

        IPage<StudentGrade> gradePageResult = studentGradeMapper.selectPage(gradePage, gradeQw);
        List<StudentGrade> grades = gradePageResult.getRecords();

        List<Map<String, Object>> gradeList = new ArrayList<>();
        for (StudentGrade sg : grades) {
            // 获取课程信息
            CoursePlan plan = coursePlanMapper.selectById(sg.getCoursePlanId());
            String courseName = "";
            String teacherName = "";
            if (plan != null) {
                Course course = courseMapper.selectById(plan.getCourseId());
                if (course != null) {
                    courseName = course.getCourseName();
                }
                if (plan.getTeacherId() != null) {
                    SysUser teacher = sysUserMapper.selectById(plan.getTeacherId());
                    teacherName = teacher != null ? (teacher.getRealName() != null ? teacher.getRealName() : teacher.getUsername()) : "";
                }
            }

            // 获取学期名称
            String semesterName = "";
            if (sg.getSemesterId() != null) {
                SysSemester sem = sysSemesterMapper.selectById(sg.getSemesterId());
                semesterName = sem != null ? sem.getSemesterName() : "";
            }

            Map<String, Object> gradeMap = new LinkedHashMap<>();
            gradeMap.put("gradeId", sg.getId());
            gradeMap.put("coursePlanId", sg.getCoursePlanId());
            gradeMap.put("courseName", courseName);
            gradeMap.put("teacherName", teacherName);
            gradeMap.put("semesterName", semesterName);
            gradeMap.put("semester", semesterName);
            gradeMap.put("usualGrade", sg.getUsualGrade());
            gradeMap.put("examGrade", sg.getExamGrade());
            gradeMap.put("experimentGrade", sg.getExperimentGrade());
            gradeMap.put("trainingGrade", sg.getTrainingGrade());
            gradeMap.put("finalGrade", sg.getFinalGrade());
            gradeMap.put("gradeComment", sg.getGradeComment());

            gradeList.add(gradeMap);
        }

        Page<Map<String, Object>> resultPage = new Page<>(gradePage.getCurrent(), gradePage.getSize());
        resultPage.setTotal(gradePageResult.getTotal());
        resultPage.setRecords(gradeList);

        return resultPage;
    }

    // ==================== 消息通知 ====================

    @Override
    public Map<String, Object> getMessages(String type, Integer isRead, Integer page, Integer pageSize) {
        Long studentId = UserContext.getUserId();

        // 1. 查询未读消息数
        QueryWrapper<SysMessage> unreadQw = new QueryWrapper<>();
        unreadQw.eq("receiver_id", studentId)
                .eq("is_read", 0);
        long unreadCount = sysMessageMapper.selectCount(unreadQw);

        // 2. 分页查询消息列表
        Page<SysMessage> msgPage = new Page<>(page, pageSize);
        QueryWrapper<SysMessage> msgQw = new QueryWrapper<>();
        msgQw.eq("receiver_id", studentId);

        if (type != null && !type.isEmpty()) {
            msgQw.eq("message_type", type);
        }
        if (isRead != null) {
            msgQw.eq("is_read", isRead);
        }
        msgQw.orderByDesc("create_time");

        IPage<SysMessage> msgPageResult = sysMessageMapper.selectPage(msgPage, msgQw);

        // 3. 将实体列表转换为前端字段名（对齐教师端）
        List<Map<String, Object>> msgList = new ArrayList<>();
        for (SysMessage msg : msgPageResult.getRecords()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("messageId", msg.getId());
            m.put("id", msg.getId());
            m.put("title", msg.getTitle());
            m.put("content", msg.getContent());
            m.put("type", msg.getMessageType());
            m.put("messageType", msg.getMessageType());
            m.put("isRead", msg.getIsRead());
            m.put("sendTime", msg.getCreateTime());
            m.put("createTime", msg.getCreateTime());
            msgList.add(m);
        }

        // 4. 组装结果（对齐教师端结构）
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("unreadCount", unreadCount);
        result.put("records", msgList);
        result.put("total", msgPageResult.getTotal());
        result.put("page", msgPageResult.getCurrent());
        result.put("pageSize", msgPageResult.getSize());

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markMessageRead(Long messageId) {
        Long studentId = UserContext.getUserId();

        SysMessage msg = sysMessageMapper.selectById(messageId);
        if (msg == null) {
            throw new BusinessException(404, "消息不存在");
        }

        if (!studentId.equals(msg.getReceiverId())) {
            throw new BusinessException(403, "无权操作该消息");
        }

        msg.setIsRead(1);
        msg.setReadTime(LocalDateTime.now());
        sysMessageMapper.updateById(msg);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAllMessagesRead() {
        Long studentId = UserContext.getUserId();

        QueryWrapper<SysMessage> unreadQw = new QueryWrapper<>();
        unreadQw.eq("receiver_id", studentId)
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

        log.info("学生 {} 将所有 {} 条消息标记为已读", studentId, unreadMessages.size());
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 获取当前登录学生的班级 ID
     */
    private BigDecimal avgList(List<BigDecimal> list) {
        if (list == null || list.isEmpty()) return BigDecimal.ZERO;
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal v : list) {
            if (v != null) sum = sum.add(v);
        }
        return sum.divide(BigDecimal.valueOf(list.size()), 2, RoundingMode.HALF_UP);
    }

    private Long getCurrentUserClassId() {
        Long studentId = UserContext.getUserId();
        SysUser user = sysUserMapper.selectById(studentId);
        return user != null ? user.getClassId() : null;
    }

    /**
     * 确定学生端任务状态
     */
    private String determineStudentTaskStatus(ExperimentTask task, StudentExperimentSubmission submission) {
        if (submission == null) {
            return "NOT_STARTED";
        }
        String subStatus = submission.getStatus();
        if (SUBMIT_STATUS_GRADED.equals(subStatus)) {
            return "GRADED";
        } else if (SUBMIT_STATUS_RETURNED.equals(subStatus)) {
            return "RETURNED";
        } else if (SUBMIT_STATUS_SUBMITTED.equals(subStatus)) {
            return "SUBMITTED";
        }
        return "IN_PROGRESS";
    }

    /**
     * 将 AcademicDiagnosis 实体转为 Map（使用前端字段名）
     */
    private Map<String, Object> buildDiagnosisMap(AcademicDiagnosis diagnosis) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("diagnosisId", diagnosis.getId());
        result.put("semesterId", diagnosis.getSemesterId());
        result.put("totalStudyHours", diagnosis.getTotalStudyHours());
        result.put("totalStudyMinutes", diagnosis.getTotalStudyHours() != null
                ? diagnosis.getTotalStudyHours().multiply(BigDecimal.valueOf(60)) : BigDecimal.ZERO);
        result.put("avgExerciseAccuracy", diagnosis.getAvgExerciseAccuracy());
        result.put("taskCompletionRate", diagnosis.getTaskCompletionRate());
        result.put("academicLevel", diagnosis.getDiagnosisLevel());
        result.put("level", diagnosis.getDiagnosisLevel());
        result.put("overallScore", diagnosis.getAvgExerciseAccuracy());
        result.put("avgScore", diagnosis.getAvgExerciseAccuracy());
        result.put("summary", diagnosis.getDiagnosisReport());
        result.put("diagnosisReport", diagnosis.getDiagnosisReport());

        // weakPoints 转为数组
        List<Map<String, Object>> weakPoints = new ArrayList<>();
        if (diagnosis.getWeakPoints() != null && !diagnosis.getWeakPoints().isEmpty()) {
            Map<String, Object> wp = new LinkedHashMap<>();
            wp.put("knowledge", diagnosis.getWeakPoints());
            wp.put("courseName", "");
            weakPoints.add(wp);
        }
        result.put("weakPoints", weakPoints);

        // suggestResources 转为数组
        List<Map<String, Object>> suggestResources = new ArrayList<>();
        if (diagnosis.getRecommendResources() != null && !diagnosis.getRecommendResources().isEmpty()) {
            Map<String, Object> sr = new LinkedHashMap<>();
            sr.put("resourceId", 0);
            sr.put("name", diagnosis.getRecommendResources());
            sr.put("type", "OTHER");
            suggestResources.add(sr);
        }
        result.put("suggestResources", suggestResources);
        result.put("suggestions", diagnosis.getRecommendResources());

        return result;
    }

}
