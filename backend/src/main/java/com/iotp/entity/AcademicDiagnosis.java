package com.iotp.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 学业诊断实体
 */
@TableName("academic_diagnosis")
public class AcademicDiagnosis {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 学生ID */
    @TableField("student_id")
    private Long studentId;

    /** 学期ID */
    @TableField("semester_id")
    private Long semesterId;

    /** 总学习时长 */
    @TableField("total_study_hours")
    private BigDecimal totalStudyHours;

    /** 平均练习正确率 */
    @TableField("avg_exercise_accuracy")
    private BigDecimal avgExerciseAccuracy;

    /** 任务完成率 */
    @TableField("task_completion_rate")
    private BigDecimal taskCompletionRate;

    /** 诊断等级 */
    @TableField("diagnosis_level")
    private String diagnosisLevel;

    /** 诊断报告 */
    @TableField("diagnosis_report")
    private String diagnosisReport;

    /** 薄弱点 */
    @TableField("weak_points")
    private String weakPoints;

    /** 推荐资源 */
    @TableField("recommend_resources")
    private String recommendResources;

    /** 生成时间 */
    @TableField("generated_time")
    private LocalDateTime generatedTime;

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

    public Long getSemesterId() {
        return semesterId;
    }

    public void setSemesterId(Long semesterId) {
        this.semesterId = semesterId;
    }

    public BigDecimal getTotalStudyHours() {
        return totalStudyHours;
    }

    public void setTotalStudyHours(BigDecimal totalStudyHours) {
        this.totalStudyHours = totalStudyHours;
    }

    public BigDecimal getAvgExerciseAccuracy() {
        return avgExerciseAccuracy;
    }

    public void setAvgExerciseAccuracy(BigDecimal avgExerciseAccuracy) {
        this.avgExerciseAccuracy = avgExerciseAccuracy;
    }

    public BigDecimal getTaskCompletionRate() {
        return taskCompletionRate;
    }

    public void setTaskCompletionRate(BigDecimal taskCompletionRate) {
        this.taskCompletionRate = taskCompletionRate;
    }

    public String getDiagnosisLevel() {
        return diagnosisLevel;
    }

    public void setDiagnosisLevel(String diagnosisLevel) {
        this.diagnosisLevel = diagnosisLevel;
    }

    public String getDiagnosisReport() {
        return diagnosisReport;
    }

    public void setDiagnosisReport(String diagnosisReport) {
        this.diagnosisReport = diagnosisReport;
    }

    public String getWeakPoints() {
        return weakPoints;
    }

    public void setWeakPoints(String weakPoints) {
        this.weakPoints = weakPoints;
    }

    public String getRecommendResources() {
        return recommendResources;
    }

    public void setRecommendResources(String recommendResources) {
        this.recommendResources = recommendResources;
    }

    public LocalDateTime getGeneratedTime() {
        return generatedTime;
    }

    public void setGeneratedTime(LocalDateTime generatedTime) {
        this.generatedTime = generatedTime;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        return "AcademicDiagnosis{" +
                "id=" + id +
                ", studentId=" + studentId +
                ", semesterId=" + semesterId +
                ", totalStudyHours=" + totalStudyHours +
                ", avgExerciseAccuracy=" + avgExerciseAccuracy +
                ", taskCompletionRate=" + taskCompletionRate +
                ", diagnosisLevel='" + diagnosisLevel + '\'' +
                ", diagnosisReport='" + diagnosisReport + '\'' +
                ", weakPoints='" + weakPoints + '\'' +
                ", recommendResources='" + recommendResources + '\'' +
                ", generatedTime=" + generatedTime +
                ", createTime=" + createTime +
                '}';
    }
}
