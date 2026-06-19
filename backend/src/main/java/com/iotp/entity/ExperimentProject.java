package com.iotp.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

/**
 * 实验项目实体
 */
@TableName("experiment_project")
public class ExperimentProject {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 项目名称 */
    @TableField("project_name")
    private String projectName;

    /** 项目描述 */
    private String description;

    /** 项目类型 */
    @TableField("project_type")
    private String projectType;

    /** 教师ID */
    @TableField("teacher_id")
    private Long teacherId;

    /** 指导文件URL */
    @TableField("guide_file_url")
    private String guideFileUrl;

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

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getProjectType() {
        return projectType;
    }

    public void setProjectType(String projectType) {
        this.projectType = projectType;
    }

    public Long getTeacherId() {
        return teacherId;
    }

    public void setTeacherId(Long teacherId) {
        this.teacherId = teacherId;
    }

    public String getGuideFileUrl() {
        return guideFileUrl;
    }

    public void setGuideFileUrl(String guideFileUrl) {
        this.guideFileUrl = guideFileUrl;
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
        return "ExperimentProject{" +
                "id=" + id +
                ", projectName='" + projectName + '\'' +
                ", description='" + description + '\'' +
                ", projectType='" + projectType + '\'' +
                ", teacherId=" + teacherId +
                ", guideFileUrl='" + guideFileUrl + '\'' +
                ", auditStatus='" + auditStatus + '\'' +
                ", auditAdminId=" + auditAdminId +
                ", auditComment='" + auditComment + '\'' +
                ", auditTime=" + auditTime +
                ", isDeleted=" + isDeleted +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                '}';
    }
}
