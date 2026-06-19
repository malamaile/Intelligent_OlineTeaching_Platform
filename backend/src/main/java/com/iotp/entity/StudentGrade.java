package com.iotp.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 学生成绩实体
 */
@TableName("student_grade")
public class StudentGrade {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 学生ID */
    @TableField("student_id")
    private Long studentId;

    /** 开课计划ID */
    @TableField("course_plan_id")
    private Long coursePlanId;

    /** 学期ID */
    @TableField("semester_id")
    private Long semesterId;

    /** 平时成绩 */
    @TableField("usual_grade")
    private BigDecimal usualGrade;

    /** 考试成绩 */
    @TableField("exam_grade")
    private BigDecimal examGrade;

    /** 实验成绩 */
    @TableField("experiment_grade")
    private BigDecimal experimentGrade;

    /** 实训成绩 */
    @TableField("training_grade")
    private BigDecimal trainingGrade;

    /** 最终成绩 */
    @TableField("final_grade")
    private BigDecimal finalGrade;

    /** 成绩评语 */
    @TableField("grade_comment")
    private String gradeComment;

    /** 是否已发布 */
    @TableField("is_published")
    private Integer isPublished;

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

    public Long getSemesterId() {
        return semesterId;
    }

    public void setSemesterId(Long semesterId) {
        this.semesterId = semesterId;
    }

    public BigDecimal getUsualGrade() {
        return usualGrade;
    }

    public void setUsualGrade(BigDecimal usualGrade) {
        this.usualGrade = usualGrade;
    }

    public BigDecimal getExamGrade() {
        return examGrade;
    }

    public void setExamGrade(BigDecimal examGrade) {
        this.examGrade = examGrade;
    }

    public BigDecimal getExperimentGrade() {
        return experimentGrade;
    }

    public void setExperimentGrade(BigDecimal experimentGrade) {
        this.experimentGrade = experimentGrade;
    }

    public BigDecimal getTrainingGrade() {
        return trainingGrade;
    }

    public void setTrainingGrade(BigDecimal trainingGrade) {
        this.trainingGrade = trainingGrade;
    }

    public BigDecimal getFinalGrade() {
        return finalGrade;
    }

    public void setFinalGrade(BigDecimal finalGrade) {
        this.finalGrade = finalGrade;
    }

    public String getGradeComment() {
        return gradeComment;
    }

    public void setGradeComment(String gradeComment) {
        this.gradeComment = gradeComment;
    }

    public Integer getIsPublished() {
        return isPublished;
    }

    public void setIsPublished(Integer isPublished) {
        this.isPublished = isPublished;
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
        return "StudentGrade{" +
                "id=" + id +
                ", studentId=" + studentId +
                ", coursePlanId=" + coursePlanId +
                ", semesterId=" + semesterId +
                ", usualGrade=" + usualGrade +
                ", examGrade=" + examGrade +
                ", experimentGrade=" + experimentGrade +
                ", trainingGrade=" + trainingGrade +
                ", finalGrade=" + finalGrade +
                ", gradeComment='" + gradeComment + '\'' +
                ", isPublished=" + isPublished +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                '}';
    }
}
