package com.iotp.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

/**
 * 开课计划实体
 */
@TableName("course_plan")
public class CoursePlan {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 课程ID */
    @TableField("course_id")
    private Long courseId;

    /** 学期ID */
    @TableField("semester_id")
    private Long semesterId;

    /** 班级ID */
    @TableField("class_id")
    private Long classId;

    /** 授课教师ID */
    @TableField("teacher_id")
    private Long teacherId;

    /** 排课信息 */
    @TableField("schedule_info")
    private String scheduleInfo;

    /** 审核状态 */
    @TableField("audit_status")
    private String auditStatus;

    /** 审核管理员ID */
    @TableField("audit_admin_id")
    private Long auditAdminId;

    /** 审核意见 */
    @TableField("audit_comment")
    private String auditComment;

    /** 审核时间 */
    @TableField("audit_time")
    private LocalDateTime auditTime;

    /** 状态 */
    private Integer status;

    /** 逻辑删除标志 */
    @TableLogic
    private Integer isDeleted;

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

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    public Long getSemesterId() {
        return semesterId;
    }

    public void setSemesterId(Long semesterId) {
        this.semesterId = semesterId;
    }

    public Long getClassId() {
        return classId;
    }

    public void setClassId(Long classId) {
        this.classId = classId;
    }

    public Long getTeacherId() {
        return teacherId;
    }

    public void setTeacherId(Long teacherId) {
        this.teacherId = teacherId;
    }

    public String getScheduleInfo() {
        return scheduleInfo;
    }

    public void setScheduleInfo(String scheduleInfo) {
        this.scheduleInfo = scheduleInfo;
    }

    public String getAuditStatus() {
        return auditStatus;
    }

    public void setAuditStatus(String auditStatus) {
        this.auditStatus = auditStatus;
    }

    public Long getAuditAdminId() {
        return auditAdminId;
    }

    public void setAuditAdminId(Long auditAdminId) {
        this.auditAdminId = auditAdminId;
    }

    public String getAuditComment() {
        return auditComment;
    }

    public void setAuditComment(String auditComment) {
        this.auditComment = auditComment;
    }

    public LocalDateTime getAuditTime() {
        return auditTime;
    }

    public void setAuditTime(LocalDateTime auditTime) {
        this.auditTime = auditTime;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getIsDeleted() {
        return isDeleted;
    }

    public void setIsDeleted(Integer isDeleted) {
        this.isDeleted = isDeleted;
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
        return "CoursePlan{" +
                "id=" + id +
                ", courseId=" + courseId +
                ", semesterId=" + semesterId +
                ", classId=" + classId +
                ", teacherId=" + teacherId +
                ", scheduleInfo='" + scheduleInfo + '\'' +
                ", auditStatus='" + auditStatus + '\'' +
                ", auditAdminId=" + auditAdminId +
                ", auditComment='" + auditComment + '\'' +
                ", auditTime=" + auditTime +
                ", status=" + status +
                ", isDeleted=" + isDeleted +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                '}';
    }
}
