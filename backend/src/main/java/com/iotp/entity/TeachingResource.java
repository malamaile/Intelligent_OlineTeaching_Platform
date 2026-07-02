package com.iotp.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

/**
 * 教学资源实体
 */
@TableName("teaching_resource")
public class TeachingResource {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 资源名称 */
    @TableField("resource_name")
    private String resourceName;

    /** 资源描述 */
    private String description;

    /** 分类ID */
    @TableField("category_id")
    private Long categoryId;

    /** 文件URL */
    @TableField("file_url")
    private String fileUrl;

    /** 文件名 */
    @TableField("file_name")
    private String fileName;

    /** 文件类型 */
    @TableField("file_type")
    private String fileType;

    /** 文件大小（字节） */
    @TableField("file_size")
    private Long fileSize;

    /** 上传教师ID */
    @TableField("teacher_id")
    private Long teacherId;

    /** 可见范围 */
    private String visibility;

    /** 关联课程ID */
    @TableField("course_id")
    private Long courseId;

    /** 关联章节ID（可为空，为空表示独立资源） */
    @TableField("chapter_id")
    private Long chapterId;

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

    /** 浏览次数 */
    @TableField("view_count")
    private Integer viewCount;

    /** 下载次数 */
    @TableField("download_count")
    private Integer downloadCount;

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

    public String getResourceName() {
        return resourceName;
    }

    public void setResourceName(String resourceName) {
        this.resourceName = resourceName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public void setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public Long getTeacherId() {
        return teacherId;
    }

    public void setTeacherId(Long teacherId) {
        this.teacherId = teacherId;
    }

    public String getVisibility() {
        return visibility;
    }

    public void setVisibility(String visibility) {
        this.visibility = visibility;
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

    public Integer getViewCount() {
        return viewCount;
    }

    public void setViewCount(Integer viewCount) {
        this.viewCount = viewCount;
    }

    public Integer getDownloadCount() {
        return downloadCount;
    }

    public void setDownloadCount(Integer downloadCount) {
        this.downloadCount = downloadCount;
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
        return "TeachingResource{" +
                "id=" + id +
                ", resourceName='" + resourceName + '\'' +
                ", description='" + description + '\'' +
                ", categoryId=" + categoryId +
                ", fileUrl='" + fileUrl + '\'' +
                ", fileName='" + fileName + '\'' +
                ", fileType='" + fileType + '\'' +
                ", fileSize=" + fileSize +
                ", teacherId=" + teacherId +
                ", visibility='" + visibility + '\'' +
                ", courseId=" + courseId +
                ", chapterId=" + chapterId +
                ", auditStatus='" + auditStatus + '\'' +
                ", auditAdminId=" + auditAdminId +
                ", auditComment='" + auditComment + '\'' +
                ", auditTime=" + auditTime +
                ", viewCount=" + viewCount +
                ", downloadCount=" + downloadCount +
                ", isDeleted=" + isDeleted +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                '}';
    }
}
