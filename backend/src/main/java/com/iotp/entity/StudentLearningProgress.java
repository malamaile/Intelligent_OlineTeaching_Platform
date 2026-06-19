package com.iotp.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

/**
 * 学生学习进度实体
 */
@TableName("student_learning_progress")
public class StudentLearningProgress {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 学生ID */
    @TableField("student_id")
    private Long studentId;

    /** 课程ID */
    @TableField("course_id")
    private Long courseId;

    /** 章节ID */
    @TableField("chapter_id")
    private Long chapterId;

    /** 已观看时长（秒） */
    @TableField("watched_duration")
    private Integer watchedDuration;

    /** 最后播放位置（秒） */
    @TableField("last_position")
    private Integer lastPosition;

    /** 是否完成 */
    @TableField("is_completed")
    private Integer isCompleted;

    /** 首次观看时间 */
    @TableField("first_watch_time")
    private LocalDateTime firstWatchTime;

    /** 最后观看时间 */
    @TableField("last_watch_time")
    private LocalDateTime lastWatchTime;

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

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    public Long getChapterId() {
        return chapterId;
    }

    public void setChapterId(Long chapterId) {
        this.chapterId = chapterId;
    }

    public Integer getWatchedDuration() {
        return watchedDuration;
    }

    public void setWatchedDuration(Integer watchedDuration) {
        this.watchedDuration = watchedDuration;
    }

    public Integer getLastPosition() {
        return lastPosition;
    }

    public void setLastPosition(Integer lastPosition) {
        this.lastPosition = lastPosition;
    }

    public Integer getIsCompleted() {
        return isCompleted;
    }

    public void setIsCompleted(Integer isCompleted) {
        this.isCompleted = isCompleted;
    }

    public LocalDateTime getFirstWatchTime() {
        return firstWatchTime;
    }

    public void setFirstWatchTime(LocalDateTime firstWatchTime) {
        this.firstWatchTime = firstWatchTime;
    }

    public LocalDateTime getLastWatchTime() {
        return lastWatchTime;
    }

    public void setLastWatchTime(LocalDateTime lastWatchTime) {
        this.lastWatchTime = lastWatchTime;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        return "StudentLearningProgress{" +
                "id=" + id +
                ", studentId=" + studentId +
                ", courseId=" + courseId +
                ", chapterId=" + chapterId +
                ", watchedDuration=" + watchedDuration +
                ", lastPosition=" + lastPosition +
                ", isCompleted=" + isCompleted +
                ", firstWatchTime=" + firstWatchTime +
                ", lastWatchTime=" + lastWatchTime +
                ", createTime=" + createTime +
                '}';
    }
}
