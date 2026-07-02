package com.iotp.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.iotp.entity.AcademicDiagnosis;
import com.iotp.entity.Course;
import com.iotp.entity.ExperimentTask;
import com.iotp.entity.StudentExperimentSubmission;
import com.iotp.entity.StudentGrade;
import com.iotp.entity.SysMessage;
import com.iotp.entity.TeachingResource;

import java.util.List;
import java.util.Map;

/**
 * 学生端业务服务接口
 * <p>提供学生首页看板、课程学习、实验任务、教学资源、学情分析、成绩查询、
 * 消息通知等功能。</p>
 */
public interface StudentService {

    // ==================== 首页看板 ====================

    /**
     * 获取学生首页看板数据
     *
     * @return 包含 todoList（待办清单）、notifications（最近通知）、todayStats（今日学习统计）的 Map
     */
    Map<String, Object> getDashboard();

    // ==================== 课程学习 ====================

    /**
     * 分页查询学生课程列表（含学习进度）
     *
     * @param keyword  课程名称关键字（可选）
     * @param semester 学期 ID（可选）
     * @param page     页码
     * @param pageSize 每页条数
     * @return 带进度信息的课程分页数据
     */
    IPage<Map<String, Object>> getCourses(String keyword, Long semester, Integer page, Integer pageSize);

    /**
     * 获取课程详情（包含章节及学习状态）
     *
     * @param courseId 课程 ID
     * @return 课程信息 + 章节列表（每章含学习状态）
     */
    Map<String, Object> getCourseDetail(Long courseId);

    /**
     * 更新学习进度
     *
     * @param courseId  课程 ID
     * @param chapterId 章节 ID
     * @param position  当前播放位置（秒）
     * @param duration  本次观看时长（秒）
     * @return 包含 chapterProgress（章节进度）、courseProgress（课程进度百分比）的 Map
     */
    Map<String, Object> updateProgress(Long courseId, Long chapterId, Integer position, Integer duration);

    /**
     * 通过邀请码加入课程
     */
    Map<String, Object> joinByInviteCode(String inviteCode);

    // ==================== 实验任务 ====================

    /**
     * 分页查询实验任务列表
     *
     * @param type     项目类型（可选，对应 ExperimentProject.projectType）
     * @param status   任务状态（可选：NOT_STARTED / IN_PROGRESS / SUBMITTED / GRADED / RETURNED）
     * @param page     页码
     * @param pageSize 每页条数
     * @return 带学生提交状态的任务分页数据
     */
    IPage<Map<String, Object>> getTasks(String type, String status, Integer page, Integer pageSize);

    /**
     * 获取实验任务详情（含指导文件和我的提交）
     *
     * @param taskId 任务 ID
     * @return 任务详情（含 ExperimentProject 信息和学生提交记录）
     */
    Map<String, Object> getTaskDetail(Long taskId);

    /**
     * 提交实验任务
     *
     * @param taskId       任务 ID
     * @param content      过程描述内容
     * @param reportFileUrl 实验报告文件 URL
     * @return 提交结果
     */
    Map<String, Object> submitTask(Long taskId, String content, String reportFileUrl);

    /**
     * 重新提交实验任务（仅在被退回时可重新提交）
     *
     * @param taskId       任务 ID
     * @param content      过程描述内容
     * @param reportFileUrl 实验报告文件 URL
     * @return 重新提交结果
     */
    Map<String, Object> resubmitTask(Long taskId, String content, String reportFileUrl);

    /**
     * 获取实验任务成绩
     *
     * @param taskId 任务 ID
     * @return 包含 score（分数）、comment（评语）、canResubmit（是否可重交）的 Map
     */
    Map<String, Object> getTaskResult(Long taskId);

    // ==================== 教学资源 ====================

    /**
     * 分页查询教学资源
     *
     * @param keyword  资源名称关键字（可选）
     * @param category 资源分类 ID（可选）
     * @param courseId 关联课程 ID（可选）
     * @param scope    可见范围（可选）
     * @param page     页码
     * @param pageSize 每页条数
     * @return 带收藏标记的资源分页数据
     */
    IPage<Map<String, Object>> getResources(String keyword, Long category, Long courseId, String scope,
                                            Integer page, Integer pageSize);

    /**
     * 收藏 / 取消收藏教学资源（切换式）
     *
     * @param resourceId 资源 ID
     * @return 操作结果，包含当前是否已收藏
     */
    Map<String, Object> favoriteResource(Long resourceId);

    /**
     * 下载教学资源，返回文件字节数组及文件名
     *
     * @param resourceId 资源 ID
     * @return [fileName, fileBytes]
     */
    Object[] downloadResource(Long resourceId);

    // ==================== 学情分析 ====================

    /**
     * 获取学情分析总览
     *
     * @param semester 学期 ID（可选，为空则查询全部）
     * @return 包含总学习时长、平均分、任务完成率、各科统计、近 4 周趋势的 Map
     */
    Map<String, Object> getAnalyticsOverview(Long semester);

    /**
     * 获取学业诊断报告
     *
     * @param semester 学期 ID（可选）
     * @return 诊断报告（含等级、薄弱点、建议）
     */
    Map<String, Object> getDiagnosis(Long semester);

    // ==================== 个人信息 ====================

    /**
     * 更新个人资料
     *
     * @param userId  用户 ID
     * @param updates 需更新的字段（avatar, realName, phone, email 等）
     */
    void updateProfile(Long userId, Map<String, Object> updates);

    // ==================== 成绩查询 ====================

    /**
     * 分页查询成绩
     *
     * @param semester 学期 ID（可选）
     * @param page     页码
     * @param pageSize 每页条数
     * @return 带课程名称的成绩分页数据
     */
    IPage<Map<String, Object>> getGrades(Long semester, Integer page, Integer pageSize);

    // ==================== 消息通知 ====================

    /**
     * 分页查询消息列表
     *
     * @param type    消息类型（可选）
     * @param isRead  是否已读（可选：0-未读，1-已读）
     * @param page    页码
     * @param pageSize 每页条数
     * @return 带 unreadCount 的消息分页数据
     */
    Map<String, Object> getMessages(String type, Integer isRead, Integer page, Integer pageSize);

    /**
     * 标记单条消息为已读
     *
     * @param messageId 消息 ID
     */
    void markMessageRead(Long messageId);

    /**
     * 标记当前学生所有消息为已读
     */
    void markAllMessagesRead();

}
