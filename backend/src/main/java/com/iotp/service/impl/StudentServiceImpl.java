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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 学生端业务服务实现
 */
@Service
public class StudentServiceImpl implements StudentService {

    private static final Logger log = LoggerFactory.getLogger(StudentServiceImpl.class);

    /** 提交状态常量 */
    private static final String SUBMIT_STATUS_SUBMITTED = "SUBMITTED";
    private static final String SUBMIT_STATUS_GRADED = "GRADED";
    private static final String SUBMIT_STATUS_RETURNED = "RETURNED";

    /** 任务审核状态常量 */
    private static final String AUDIT_STATUS_APPROVED = "APPROVED";

    /** 资源审核状态常量 */
    private static final String RESOURCE_AUDIT_APPROVED = "APPROVED";

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
    private StudentFavoriteResourceMapper favoriteResourceMapper;

    @Autowired
    private StudentGradeMapper studentGradeMapper;

    @Autowired
    private LearningAnalyticsDailyMapper analyticsDailyMapper;

    @Autowired
    private AcademicDiagnosisMapper academicDiagnosisMapper;

    @Autowired
    private SysAnnouncementMapper sysAnnouncementMapper;

    @Autowired
    private SysMessageMapper sysMessageMapper;

    @Autowired
    private SysSemesterMapper sysSemesterMapper;

    // ==================== 首页看板 ====================

    @Override
    public Map<String, Object> getDashboard() {
        Long studentId = UserContext.getUserId();

        // 1. 构建待办清单
        List<Map<String, Object>> todoList = new ArrayList<>();

        // 1.1 查询该学生未完成的课程章节
        QueryWrapper<StudentCourseEnrollment> enrollQw = new QueryWrapper<>();
        enrollQw.eq("student_id", studentId)
                .eq("is_completed", 0);
        List<StudentCourseEnrollment> enrollments = enrollmentMapper.selectList(enrollQw);

        // 获取所有 enrolled 的课程 plan id
        Set<Long> planIds = enrollments.stream()
                .map(StudentCourseEnrollment::getCoursePlanId)
                .collect(Collectors.toSet());

        if (!planIds.isEmpty()) {
            // 根据 course_plan 获取 course_id
            List<CoursePlan> plans = coursePlanMapper.selectBatchIds(planIds);
            Set<Long> courseIds = plans.stream()
                    .map(CoursePlan::getCourseId)
                    .collect(Collectors.toSet());

            if (!courseIds.isEmpty()) {
                // 查找这些课程中未完成的章节
                for (Long courseId : courseIds) {
                    QueryWrapper<CourseChapter> chQw = new QueryWrapper<>();
                    chQw.eq("course_id", courseId)
                            .orderByAsc("chapter_order");
                    List<CourseChapter> chapters = courseChapterMapper.selectList(chQw);

                    for (CourseChapter chapter : chapters) {
                        // 查询学习进度
                        QueryWrapper<StudentLearningProgress> lpQw = new QueryWrapper<>();
                        lpQw.eq("student_id", studentId)
                                .eq("course_id", courseId)
                                .eq("chapter_id", chapter.getId());
                        StudentLearningProgress progress = learningProgressMapper.selectOne(lpQw);

                        boolean completed = progress != null
                                && Integer.valueOf(1).equals(progress.getIsCompleted());

                        if (!completed) {
                            Map<String, Object> todo = new LinkedHashMap<>();
                            todo.put("type", "CHAPTER");
                            todo.put("courseId", courseId);
                            todo.put("chapterId", chapter.getId());
                            todo.put("chapterName", chapter.getChapterName());
                            todo.put("title", "学习章节：" + chapter.getChapterName());
                            todoList.add(todo);
                        }
                    }
                }
            }
        }

        // 1.2 查询未提交的实验任务
        Long classId = getCurrentUserClassId();
        if (classId != null) {
            QueryWrapper<ExperimentTask> taskQw = new QueryWrapper<>();
            taskQw.eq("class_id", classId)
                    .eq("audit_status", AUDIT_STATUS_APPROVED);
            List<ExperimentTask> tasks = experimentTaskMapper.selectList(taskQw);

            for (ExperimentTask task : tasks) {
                // 检查是否已提交
                QueryWrapper<StudentExperimentSubmission> subQw = new QueryWrapper<>();
                subQw.eq("task_id", task.getId())
                        .eq("student_id", studentId);
                StudentExperimentSubmission submission = submissionMapper.selectOne(subQw);

                boolean submitted = submission != null
                        && (SUBMIT_STATUS_SUBMITTED.equals(submission.getStatus())
                        || SUBMIT_STATUS_GRADED.equals(submission.getStatus()));

                if (!submitted) {
                    Map<String, Object> todo = new LinkedHashMap<>();
                    todo.put("type", "TASK");
                    todo.put("taskId", task.getId());
                    // 获取项目名称
                    ExperimentProject project = experimentProjectMapper.selectById(task.getProjectId());
                    String projectName = project != null ? project.getProjectName() : "未知实验";
                    todo.put("title", "提交实验：" + projectName);
                    todo.put("deadline", task.getEndTime());
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
            item.put("time", ann.getPublishTime());
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
            item.put("time", msg.getCreateTime());
            item.put("isRead", msg.getIsRead());
            notifications.add(item);
        }

        // 按时间排序，取前 5 条
        notifications.sort((a, b) -> {
            LocalDateTime ta = (LocalDateTime) a.get("time");
            LocalDateTime tb = (LocalDateTime) b.get("time");
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
        todayStats.put("todayStudyMinutes", todayDuration);

        // 3.2 本周完成课程数
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        QueryWrapper<LearningAnalyticsDaily> weekQw = new QueryWrapper<>();
        weekQw.eq("student_id", studentId)
                .between("stat_date", weekStart, weekEnd)
                .gt("tasks_completed", 0);
        List<LearningAnalyticsDaily> weekRecords = analyticsDailyMapper.selectList(weekQw);
        int weekCompletedTasks = weekRecords.stream()
                .mapToInt(r -> r.getTasksCompleted() != null ? r.getTasksCompleted() : 0)
                .sum();
        todayStats.put("weekCompletedTasks", weekCompletedTasks);

        // 3.3 本周学习时长
        int weekDuration = 0;
        QueryWrapper<LearningAnalyticsDaily> weekDurQw = new QueryWrapper<>();
        weekDurQw.eq("student_id", studentId)
                .between("stat_date", weekStart, weekEnd);
        List<LearningAnalyticsDaily> weekDurRecords = analyticsDailyMapper.selectList(weekDurQw);
        weekDuration = weekDurRecords.stream()
                .mapToInt(r -> r.getStudyDurationMinutes() != null ? r.getStudyDurationMinutes() : 0)
                .sum();
        todayStats.put("weekStudyMinutes", weekDuration);

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
            // 学生未分配班级，返回空分页
            return new Page<>(page, pageSize);
        }

        // 1. 创建分页对象
        Page<CoursePlan> planPage = new Page<>(page, pageSize);

        // 2. 查询 CoursePlan
        QueryWrapper<CoursePlan> planQw = new QueryWrapper<>();
        planQw.eq("class_id", classId);
        if (semester != null) {
            planQw.eq("semester_id", semester);
        }
        planQw.orderByDesc("create_time");

        IPage<CoursePlan> planPageResult = coursePlanMapper.selectPage(planPage, planQw);
        List<CoursePlan> plans = planPageResult.getRecords();

        // 3. 组装课程数据
        List<Map<String, Object>> courseList = new ArrayList<>();
        for (CoursePlan plan : plans) {
            Course course = courseMapper.selectById(plan.getCourseId());
            if (course == null) {
                continue;
            }

            // 关键字过滤（手动过滤，因为 userMapper 无法自动 join）
            if (keyword != null && !keyword.isEmpty()
                    && !course.getCourseName().contains(keyword)) {
                continue;
            }

            // 查询该课程的选课记录以获取进度
            QueryWrapper<StudentCourseEnrollment> enrollQw = new QueryWrapper<>();
            enrollQw.eq("student_id", studentId)
                    .eq("course_plan_id", plan.getId());
            StudentCourseEnrollment enrollment = enrollmentMapper.selectOne(enrollQw);

            Map<String, Object> courseMap = new LinkedHashMap<>();
            courseMap.put("courseId", course.getId());
            courseMap.put("courseName", course.getCourseName());
            courseMap.put("courseCode", course.getCourseCode());
            courseMap.put("description", course.getDescription());
            courseMap.put("coverImage", course.getCoverImage());
            courseMap.put("planId", plan.getId());
            courseMap.put("semesterId", plan.getSemesterId());
            courseMap.put("teacherId", plan.getTeacherId());

            BigDecimal progress = BigDecimal.ZERO;
            boolean completed = false;
            if (enrollment != null) {
                progress = enrollment.getProgressPercent() != null
                        ? enrollment.getProgressPercent() : BigDecimal.ZERO;
                completed = Integer.valueOf(1).equals(enrollment.getIsCompleted());
            }
            courseMap.put("progress", progress);
            courseMap.put("completed", completed);

            courseList.add(courseMap);
        }

        // 4. 构建分页结果
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

        // 2. 查询所有章节，按排序字段升序
        QueryWrapper<CourseChapter> chapterQw = new QueryWrapper<>();
        chapterQw.eq("course_id", courseId)
                .orderByAsc("chapter_order");
        List<CourseChapter> chapters = courseChapterMapper.selectList(chapterQw);

        // 3. 查询每个章节的学习状态
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
            chapterMap.put("chapterName", chapter.getChapterName());
            chapterMap.put("chapterOrder", chapter.getChapterOrder());
            chapterMap.put("videoUrl", chapter.getVideoUrl());
            chapterMap.put("videoDuration", chapter.getVideoDuration());
            chapterMap.put("contentText", chapter.getContentText());
            chapterMap.put("attachmentUrl", chapter.getAttachmentUrl());
            chapterMap.put("status", status);

            if (progress != null) {
                chapterMap.put("watchedDuration", progress.getWatchedDuration());
                chapterMap.put("lastPosition", progress.getLastPosition());
            } else {
                chapterMap.put("watchedDuration", 0);
                chapterMap.put("lastPosition", 0);
            }

            chapterList.add(chapterMap);
        }

        // 4. 计算课程整体进度
        BigDecimal courseProgress = BigDecimal.ZERO;
        if (!chapters.isEmpty()) {
            courseProgress = BigDecimal.valueOf(completedCount)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(chapters.size()), 2, RoundingMode.HALF_UP);
        }

        // 5. 组装结果
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("courseId", course.getId());
        result.put("courseName", course.getCourseName());
        result.put("courseCode", course.getCourseCode());
        result.put("description", course.getDescription());
        result.put("coverImage", course.getCoverImage());
        result.put("courseProgress", courseProgress);
        result.put("totalChapters", chapters.size());
        result.put("completedChapters", completedCount);
        result.put("chapters", chapterList);

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> updateProgress(Long courseId, Long chapterId, Integer position, Integer duration) {
        Long studentId = UserContext.getUserId();

        // 1. 获取章节信息（用于 videoDuration 判断）
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

        // 4. 判断是否完成：播放位置 >= 视频时长
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
        // 通过 course_plan 找到 enrollment
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

        // 7. 返回结果
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

        // 根据任务状态过滤（数据库中的 status 字段）
        if (status != null && !status.isEmpty()) {
            // 前端传的状态是学生端语义，需要转换为数据库状态
            // 但数据库 ExperimentTask.status 是任务本身的状态，不是学生提交状态
            // 所以我们不过滤数据库 status，而是下面按学生提交状态过滤
        }

        taskQw.orderByDesc("create_time");
        IPage<ExperimentTask> taskPageResult = experimentTaskMapper.selectPage(taskPage, taskQw);
        List<ExperimentTask> tasks = taskPageResult.getRecords();

        // 2. 组装数据
        List<Map<String, Object>> taskList = new ArrayList<>();
        for (ExperimentTask task : tasks) {
            // 获取项目信息
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

            Map<String, Object> taskMap = new LinkedHashMap<>();
            taskMap.put("taskId", task.getId());
            taskMap.put("projectId", task.getProjectId());
            taskMap.put("projectName", project != null ? project.getProjectName() : "");
            taskMap.put("projectType", project != null ? project.getProjectType() : "");
            taskMap.put("startTime", task.getStartTime());
            taskMap.put("endTime", task.getEndTime());
            taskMap.put("status", task.getStatus());
            taskMap.put("studentStatus", studentStatus);
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

        // 4. 组装结果
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", task.getId());
        result.put("startTime", task.getStartTime());
        result.put("endTime", task.getEndTime());
        result.put("status", task.getStatus());
        result.put("isOverdue", task.getEndTime() != null
                && task.getEndTime().isBefore(LocalDateTime.now()));

        // 项目信息
        if (project != null) {
            Map<String, Object> projectInfo = new LinkedHashMap<>();
            projectInfo.put("projectId", project.getId());
            projectInfo.put("projectName", project.getProjectName());
            projectInfo.put("description", project.getDescription());
            projectInfo.put("projectType", project.getProjectType());
            projectInfo.put("guideFileUrl", project.getGuideFileUrl());
            result.put("project", projectInfo);
        }

        // 提交信息
        if (submission != null) {
            Map<String, Object> mySubmission = new LinkedHashMap<>();
            mySubmission.put("id", submission.getId());
            mySubmission.put("processDescription", submission.getProcessDescription());
            mySubmission.put("reportFileUrl", submission.getReportFileUrl());
            mySubmission.put("reportFileName", submission.getReportFileName());
            mySubmission.put("score", submission.getScore());
            mySubmission.put("teacherComment", submission.getTeacherComment());
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

        // 1. 检查任务是否存在
        ExperimentTask task = experimentTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(404, "实验任务不存在");
        }

        // 2. 检查是否已过期
        if (task.getEndTime() != null && task.getEndTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(400, "任务已截止，无法提交");
        }

        // 3. 检查是否已经提交过（防止重复提交）
        QueryWrapper<StudentExperimentSubmission> existQw = new QueryWrapper<>();
        existQw.eq("task_id", taskId)
                .eq("student_id", studentId);
        StudentExperimentSubmission existing = submissionMapper.selectOne(existQw);
        if (existing != null) {
            throw new BusinessException(409, "已提交过该任务，请使用重新提交功能");
        }

        // 4. 创建提交记录
        StudentExperimentSubmission submission = new StudentExperimentSubmission();
        submission.setTaskId(taskId);
        submission.setStudentId(studentId);
        submission.setProcessDescription(content);
        submission.setReportFileUrl(reportFileUrl);
        submission.setStatus(SUBMIT_STATUS_SUBMITTED);
        submission.setSubmitTime(LocalDateTime.now());
        submission.setResubmitCount(0);

        submissionMapper.insert(submission);

        log.info("学生 {} 提交了任务 {}", studentId, taskId);

        // 5. 返回结果
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

        // 1. 检查任务是否存在
        ExperimentTask task = experimentTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(404, "实验任务不存在");
        }

        // 2. 检查是否已过期
        if (task.getEndTime() != null && task.getEndTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(400, "任务已截止，无法重新提交");
        }

        // 3. 查找现有提交记录
        QueryWrapper<StudentExperimentSubmission> existQw = new QueryWrapper<>();
        existQw.eq("task_id", taskId)
                .eq("student_id", studentId);
        StudentExperimentSubmission submission = submissionMapper.selectOne(existQw);

        if (submission == null) {
            throw new BusinessException(400, "请先提交后再重新提交");
        }

        // 4. 检查是否处于被退回状态
        if (!SUBMIT_STATUS_RETURNED.equals(submission.getStatus())) {
            throw new BusinessException(400, "当前状态不允许重新提交，仅在被退回时可重新提交");
        }

        // 5. 检查重交次数是否超过限制
        int retryCount = submission.getResubmitCount() != null ? submission.getResubmitCount() : 0;
        if (retryCount >= DEFAULT_MAX_RETRY_COUNT) {
            throw new BusinessException(400, "已达到最大重交次数（" + DEFAULT_MAX_RETRY_COUNT + "次），无法继续重交");
        }

        // 6. 更新提交记录
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

        // 7. 返回结果
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

        // 1. 查找提交记录
        QueryWrapper<StudentExperimentSubmission> subQw = new QueryWrapper<>();
        subQw.eq("task_id", taskId)
                .eq("student_id", studentId);
        StudentExperimentSubmission submission = submissionMapper.selectOne(subQw);

        if (submission == null) {
            throw new BusinessException(404, "尚未提交该任务");
        }

        // 2. 确定是否可以重新提交
        boolean canResubmit = SUBMIT_STATUS_RETURNED.equals(submission.getStatus())
                && (submission.getResubmitCount() == null
                || submission.getResubmitCount() < DEFAULT_MAX_RETRY_COUNT);

        // 3. 组装结果
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("submissionId", submission.getId());
        result.put("score", submission.getScore());
        result.put("teacherComment", submission.getTeacherComment());
        result.put("status", submission.getStatus());
        result.put("submitTime", submission.getSubmitTime());
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
        resQw.eq("audit_status", RESOURCE_AUDIT_APPROVED);

        // 关键字过滤
        if (keyword != null && !keyword.isEmpty()) {
            resQw.like("resource_name", keyword);
        }

        // 分类过滤（对应 category_id）
        if (category != null) {
            resQw.eq("category_id", category);
        }

        // 课程过滤
        if (courseId != null) {
            resQw.eq("course_id", courseId);
        }

        // 可见范围过滤
        if (scope != null && !scope.isEmpty()) {
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

        // 3. 组装数据
        List<Map<String, Object>> resourceList = new ArrayList<>();
        for (TeachingResource res : resources) {
            Map<String, Object> resMap = new LinkedHashMap<>();
            resMap.put("resourceId", res.getId());
            resMap.put("resourceName", res.getResourceName());
            resMap.put("description", res.getDescription());
            resMap.put("categoryId", res.getCategoryId());
            resMap.put("fileUrl", res.getFileUrl());
            resMap.put("fileName", res.getFileName());
            resMap.put("fileType", res.getFileType());
            resMap.put("fileSize", res.getFileSize());
            resMap.put("teacherId", res.getTeacherId());
            resMap.put("visibility", res.getVisibility());
            resMap.put("courseId", res.getCourseId());
            resMap.put("viewCount", res.getViewCount());
            resMap.put("downloadCount", res.getDownloadCount());
            resMap.put("createTime", res.getCreateTime());
            resMap.put("isFavorited", favoritedIds.contains(res.getId()));

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

        // 1. 检查资源是否存在
        TeachingResource resource = teachingResourceMapper.selectById(resourceId);
        if (resource == null) {
            throw new BusinessException(404, "资源不存在");
        }

        // 2. 查询是否已收藏
        QueryWrapper<StudentFavoriteResource> favQw = new QueryWrapper<>();
        favQw.eq("student_id", studentId)
                .eq("resource_id", resourceId);
        StudentFavoriteResource fav = favoriteResourceMapper.selectOne(favQw);

        boolean isFavorited;
        if (fav == null) {
            // 未收藏 → 添加收藏
            StudentFavoriteResource newFav = new StudentFavoriteResource();
            newFav.setStudentId(studentId);
            newFav.setResourceId(resourceId);
            favoriteResourceMapper.insert(newFav);
            isFavorited = true;
            log.info("学生 {} 收藏了资源 {}", studentId, resourceId);
        } else {
            // 已收藏 → 取消收藏
            favoriteResourceMapper.deleteById(fav.getId());
            isFavorited = false;
            log.info("学生 {} 取消了资源 {} 的收藏", studentId, resourceId);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("resourceId", resourceId);
        result.put("isFavorited", isFavorited);

        return result;
    }

    // ==================== 学情分析 ====================

    @Override
    public Map<String, Object> getAnalyticsOverview(Long semester) {
        Long studentId = UserContext.getUserId();

        // 1. 总学习时长（分钟）
        QueryWrapper<LearningAnalyticsDaily> durQw = new QueryWrapper<>();
        durQw.eq("student_id", studentId);
        if (semester != null) {
            // 通过 semester 的开始/结束日期过滤
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

        // 4. 各科统计（按课程分组的学习时长和任务完成数）
        List<Map<String, Object>> courseStats = new ArrayList<>();
        Map<Long, List<LearningAnalyticsDaily>> dailyByCourse = allDaily.stream()
                .filter(d -> d.getCourseId() != null)
                .collect(Collectors.groupingBy(LearningAnalyticsDaily::getCourseId));
        for (Map.Entry<Long, List<LearningAnalyticsDaily>> entry : dailyByCourse.entrySet()) {
            Long cid = entry.getKey();
            Course course = courseMapper.selectById(cid);
            if (course == null) continue;

            int courseMinutes = entry.getValue().stream()
                    .mapToInt(d -> d.getStudyDurationMinutes() != null ? d.getStudyDurationMinutes() : 0)
                    .sum();
            int courseTasks = entry.getValue().stream()
                    .mapToInt(d -> d.getTasksCompleted() != null ? d.getTasksCompleted() : 0)
                    .sum();

            Map<String, Object> cs = new LinkedHashMap<>();
            cs.put("courseId", cid);
            cs.put("courseName", course.getCourseName());
            cs.put("studyMinutes", courseMinutes);
            cs.put("tasksCompleted", courseTasks);
            courseStats.add(cs);
        }

        // 5. 近 4 周学习趋势
        List<Map<String, Object>> weeklyTrend = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = 3; i >= 0; i--) {
            LocalDate weekStart = today.minusWeeks(i)
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            LocalDate weekEnd = weekStart.plusDays(6);

            QueryWrapper<LearningAnalyticsDaily> weekQw = new QueryWrapper<>();
            weekQw.eq("student_id", studentId)
                    .between("stat_date", weekStart, weekEnd);
            List<LearningAnalyticsDaily> weekData = analyticsDailyMapper.selectList(weekQw);

            int weekMin = weekData.stream()
                    .mapToInt(d -> d.getStudyDurationMinutes() != null ? d.getStudyDurationMinutes() : 0)
                    .sum();
            int weekTasks = weekData.stream()
                    .mapToInt(d -> d.getTasksCompleted() != null ? d.getTasksCompleted() : 0)
                    .sum();

            Map<String, Object> week = new LinkedHashMap<>();
            week.put("weekStart", weekStart);
            week.put("weekEnd", weekEnd);
            week.put("studyMinutes", weekMin);
            week.put("tasksCompleted", weekTasks);
            weeklyTrend.add(week);
        }

        // 6. 组装结果
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalStudyMinutes", totalStudyMinutes);
        result.put("avgScore", avgScore);
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
            // 已有诊断记录，直接返回
            return buildDiagnosisMap(diagnosis);
        }

        // 2. 没有诊断记录，根据成绩和统计计算一个简单的诊断
        // 获取平均分
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

        // 获取学习时长
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

        // 计算诊断等级和建议
        String level;
        String report;
        String weakPoints;
        String suggestions;

        if (avgFinalGrade.compareTo(BigDecimal.valueOf(90)) >= 0) {
            level = "优秀";
            report = "学习成绩优异，继续保持。";
            weakPoints = "无明显薄弱点";
            suggestions = "建议挑战更高难度的内容，拓展知识面。";
        } else if (avgFinalGrade.compareTo(BigDecimal.valueOf(80)) >= 0) {
            level = "良好";
            report = "学习成绩良好，有提升空间。";
            weakPoints = "部分知识点掌握不够深入";
            suggestions = "建议针对薄弱环节加强练习，多查阅相关资料。";
        } else if (avgFinalGrade.compareTo(BigDecimal.valueOf(70)) >= 0) {
            level = "中等";
            report = "学习成绩中等，需加强学习。";
            weakPoints = "基础知识点存在漏洞";
            suggestions = "建议回顾课程视频和教材，完成课后练习巩固知识。";
        } else if (avgFinalGrade.compareTo(BigDecimal.valueOf(60)) >= 0) {
            level = "及格";
            report = "学习成绩偏低，需加倍努力。";
            weakPoints = "基础知识掌握不牢固";
            suggestions = "建议制定学习计划，从基础开始复习，多向老师和同学请教。";
        } else {
            level = "需努力";
            report = "学习成绩不理想，需要重点关注。";
            weakPoints = "多个知识点掌握不足";
            suggestions = "建议与任课老师沟通，制定个性化补习计划，利用课余时间加强学习。";
        }

        if (totalMinutes < 300) {
            suggestions += "建议增加学习时间，保证每天至少 1-2 小时的学习时长。";
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("level", level);
        result.put("diagnosisReport", report);
        result.put("weakPoints", weakPoints);
        result.put("suggestions", suggestions);
        result.put("avgScore", avgFinalGrade);
        result.put("totalStudyMinutes", totalMinutes);
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

        // 更新可修改的字段
        if (updates.containsKey("avatar")) {
            user.setAvatar((String) updates.get("avatar"));
        }
        if (updates.containsKey("realName")) {
            user.setRealName((String) updates.get("realName"));
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

        // 1. 分页查询成绩
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

        // 2. 组装数据（关联课程名称）
        List<Map<String, Object>> gradeList = new ArrayList<>();
        for (StudentGrade sg : grades) {
            // 获取课程信息
            CoursePlan plan = coursePlanMapper.selectById(sg.getCoursePlanId());
            String courseName = "";
            if (plan != null) {
                Course course = courseMapper.selectById(plan.getCourseId());
                if (course != null) {
                    courseName = course.getCourseName();
                }
            }

            // 获取学期名称
            String semesterName = "";
            if (sg.getSemesterId() != null) {
                SysSemester sem = sysSemesterMapper.selectById(sg.getSemesterId());
                if (sem != null) {
                    semesterName = sem.getSemesterName();
                }
            }

            Map<String, Object> gradeMap = new LinkedHashMap<>();
            gradeMap.put("gradeId", sg.getId());
            gradeMap.put("coursePlanId", sg.getCoursePlanId());
            gradeMap.put("courseName", courseName);
            gradeMap.put("semesterName", semesterName);
            gradeMap.put("usualGrade", sg.getUsualGrade());
            gradeMap.put("examGrade", sg.getExamGrade());
            gradeMap.put("experimentGrade", sg.getExperimentGrade());
            gradeMap.put("trainingGrade", sg.getTrainingGrade());
            gradeMap.put("finalGrade", sg.getFinalGrade());
            gradeMap.put("gradeComment", sg.getGradeComment());

            gradeList.add(gradeMap);
        }

        // 3. 构建分页结果
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

        // 3. 组装结果
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("unreadCount", unreadCount);
        result.put("records", msgPageResult.getRecords());
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

        // 校验是否是自己的消息
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

        // 批量更新所有未读消息
        SysMessage updateMsg = new SysMessage();
        updateMsg.setIsRead(1);
        updateMsg.setReadTime(LocalDateTime.now());

        QueryWrapper<SysMessage> updateQw = new QueryWrapper<>();
        updateQw.eq("receiver_id", studentId)
                .eq("is_read", 0);

        sysMessageMapper.update(updateMsg, updateQw);

        log.info("学生 {} 已标记所有消息为已读", studentId);
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 获取当前登录学生的班级 ID
     */
    private Long getCurrentUserClassId() {
        Long studentId = UserContext.getUserId();
        SysUser user = sysUserMapper.selectById(studentId);
        return user != null ? user.getClassId() : null;
    }

    /**
     * 确定学生端任务状态
     *
     * @param task       实验任务
     * @param submission 学生提交记录（可为 null）
     * @return NOT_STARTED / IN_PROGRESS / SUBMITTED / GRADED / RETURNED
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
     * 将 AcademicDiagnosis 实体转为 Map
     */
    private Map<String, Object> buildDiagnosisMap(AcademicDiagnosis diagnosis) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("diagnosisId", diagnosis.getId());
        result.put("semesterId", diagnosis.getSemesterId());
        result.put("totalStudyHours", diagnosis.getTotalStudyHours());
        result.put("avgExerciseAccuracy", diagnosis.getAvgExerciseAccuracy());
        result.put("taskCompletionRate", diagnosis.getTaskCompletionRate());
        result.put("level", diagnosis.getDiagnosisLevel());
        result.put("diagnosisReport", diagnosis.getDiagnosisReport());
        result.put("weakPoints", diagnosis.getWeakPoints());
        result.put("suggestions", diagnosis.getRecommendResources());
        result.put("generatedTime", diagnosis.getGeneratedTime());
        return result;
    }

}
