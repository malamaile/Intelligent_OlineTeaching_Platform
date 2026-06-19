package com.iotp.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 学生实验提交实体
 */
@TableName("student_experiment_submission")
public class StudentExperimentSubmission {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 实验任务ID */
    @TableField("task_id")
    private Long taskId;

    /** 学生ID */
    @TableField("student_id")
    private Long studentId;

    /** 过程描述 */
    @TableField("process_description")
    private String processDescription;

    /** 实验报告文件URL */
    @TableField("report_file_url")
    private String reportFileUrl;

    /** 实验报告文件名 */
    @TableField("report_file_name")
    private String reportFileName;

    /** 成绩 */
    private BigDecimal score;

    /** 教师评语 */
    @TableField("teacher_comment")
    private String teacherComment;

    /** 状态 */
    private String status;

    /** 提交时间 */
    @TableField("submit_time")
    private LocalDateTime submitTime;

    /** 评分时间 */
    @TableField("grade_time")
    private LocalDateTime gradeTime;

    /** 重交次数 */
    @TableField("resubmit_count")
    private Integer resubmitCount;

    /** 逻辑删除标志 */
    @TableLogic
    private Integer isDeleted;

    /** 创建时间，自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public String getProcessDescription() {
        return processDescription;
    }

    public void setProcessDescription(String processDescription) {
        this.processDescription = processDescription;
    }

    public String getReportFileUrl() {
        return reportFileUrl;
    }

    public void setReportFileUrl(String reportFileUrl) {
        this.reportFileUrl = reportFileUrl;
    }

    public String getReportFileName() {
        return reportFileName;
    }

    public void setReportFileName(String reportFileName) {
        this.reportFileName = reportFileName;
    }

    public BigDecimal getScore() {
        return score;
    }

    public void setScore(BigDecimal score) {
        this.score = score;
    }

    public String getTeacherComment() {
        return teacherComment;
    }

    public void setTeacherComment(String teacherComment) {
        this.teacherComment = teacherComment;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getSubmitTime() {
        return submitTime;
    }

    public void setSubmitTime(LocalDateTime submitTime) {
        this.submitTime = submitTime;
    }

    public LocalDateTime getGradeTime() {
        return gradeTime;
    }

    public void setGradeTime(LocalDateTime gradeTime) {
        this.gradeTime = gradeTime;
    }

    public Integer getResubmitCount() {
        return resubmitCount;
    }

    public void setResubmitCount(Integer resubmitCount) {
        this.resubmitCount = resubmitCount;
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

    @Override
    public String toString() {
        return "StudentExperimentSubmission{" +
                "id=" + id +
                ", taskId=" + taskId +
                ", studentId=" + studentId +
                ", processDescription='" + processDescription + '\'' +
                ", reportFileUrl='" + reportFileUrl + '\'' +
                ", reportFileName='" + reportFileName + '\'' +
                ", score=" + score +
                ", teacherComment='" + teacherComment + '\'' +
                ", status='" + status + '\'' +
                ", submitTime=" + submitTime +
                ", gradeTime=" + gradeTime +
                ", resubmitCount=" + resubmitCount +
                ", isDeleted=" + isDeleted +
                ", createTime=" + createTime +
                '}';
    }
}
