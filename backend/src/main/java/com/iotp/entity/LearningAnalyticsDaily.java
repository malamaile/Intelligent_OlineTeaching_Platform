package com.iotp.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 学习分析日报实体
 */
@TableName("learning_analytics_daily")
public class LearningAnalyticsDaily {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 学生ID */
    @TableField("student_id")
    private Long studentId;

    /** 统计日期 */
    @TableField("stat_date")
    private LocalDate statDate;

    /** 课程ID */
    @TableField("course_id")
    private Long courseId;

    /** 学习时长（分钟） */
    @TableField("study_duration_minutes")
    private Integer studyDurationMinutes;

    /** 完成练习数 */
    @TableField("exercises_completed")
    private Integer exercisesCompleted;

    /** 练习正确数 */
    @TableField("exercises_correct")
    private Integer exercisesCorrect;

    /** 完成任务数 */
    @TableField("tasks_completed")
    private Integer tasksCompleted;

    /** 创建时间，自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public LocalDate getStatDate() {
        return statDate;
    }

    public void setStatDate(LocalDate statDate) {
        this.statDate = statDate;
    }

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    public Integer getStudyDurationMinutes() {
        return studyDurationMinutes;
    }

    public void setStudyDurationMinutes(Integer studyDurationMinutes) {
        this.studyDurationMinutes = studyDurationMinutes;
    }

    public Integer getExercisesCompleted() {
        return exercisesCompleted;
    }

    public void setExercisesCompleted(Integer exercisesCompleted) {
        this.exercisesCompleted = exercisesCompleted;
    }

    public Integer getExercisesCorrect() {
        return exercisesCorrect;
    }

    public void setExercisesCorrect(Integer exercisesCorrect) {
        this.exercisesCorrect = exercisesCorrect;
    }

    public Integer getTasksCompleted() {
        return tasksCompleted;
    }

    public void setTasksCompleted(Integer tasksCompleted) {
        this.tasksCompleted = tasksCompleted;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        return "LearningAnalyticsDaily{" +
                "id=" + id +
                ", studentId=" + studentId +
                ", statDate=" + statDate +
                ", courseId=" + courseId +
                ", studyDurationMinutes=" + studyDurationMinutes +
                ", exercisesCompleted=" + exercisesCompleted +
                ", exercisesCorrect=" + exercisesCorrect +
                ", tasksCompleted=" + tasksCompleted +
                ", createTime=" + createTime +
                '}';
    }
}
