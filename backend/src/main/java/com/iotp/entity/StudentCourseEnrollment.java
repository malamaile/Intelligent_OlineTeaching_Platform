package com.iotp.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 学生选课记录实体
 */
@TableName("student_course_enrollment")
public class StudentCourseEnrollment {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 学生ID */
    @TableField("student_id")
    private Long studentId;

    /** 开课计划ID */
    @TableField("course_plan_id")
    private Long coursePlanId;

    /** 学习进度百分比 */
    @TableField("progress_percent")
    private BigDecimal progressPercent;

    /** 是否完成 */
    @TableField("is_completed")
    private Integer isCompleted;

    /** 选课时间 */
    @TableField("enroll_time")
    private LocalDateTime enrollTime;

    /** 创建时间，自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间，自动填充 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

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

    public Long getCoursePlanId() {
        return coursePlanId;
    }

    public void setCoursePlanId(Long coursePlanId) {
        this.coursePlanId = coursePlanId;
    }

    public BigDecimal getProgressPercent() {
        return progressPercent;
    }

    public void setProgressPercent(BigDecimal progressPercent) {
        this.progressPercent = progressPercent;
    }

    public Integer getIsCompleted() {
        return isCompleted;
    }

    public void setIsCompleted(Integer isCompleted) {
        this.isCompleted = isCompleted;
    }

    public LocalDateTime getEnrollTime() {
        return enrollTime;
    }

    public void setEnrollTime(LocalDateTime enrollTime) {
        this.enrollTime = enrollTime;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    @Override
    public String toString() {
        return "StudentCourseEnrollment{" +
                "id=" + id +
                ", studentId=" + studentId +
                ", coursePlanId=" + coursePlanId +
                ", progressPercent=" + progressPercent +
                ", isCompleted=" + isCompleted +
                ", enrollTime=" + enrollTime +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                '}';
    }
}
