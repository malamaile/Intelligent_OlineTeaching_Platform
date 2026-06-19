package com.iotp.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 学业诊断规则实体
 */
@TableName("academic_diagnosis_rule")
public class AcademicDiagnosisRule {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 规则名称 */
    @TableField("rule_name")
    private String ruleName;

    /** 诊断等级 */
    @TableField("diagnosis_level")
    private String diagnosisLevel;

    /** 最低分数 */
    @TableField("min_score")
    private BigDecimal minScore;

    /** 最高分数 */
    @TableField("max_score")
    private BigDecimal maxScore;

    /** 规则描述 */
    private String description;

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

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public String getDiagnosisLevel() {
        return diagnosisLevel;
    }

    public void setDiagnosisLevel(String diagnosisLevel) {
        this.diagnosisLevel = diagnosisLevel;
    }

    public BigDecimal getMinScore() {
        return minScore;
    }

    public void setMinScore(BigDecimal minScore) {
        this.minScore = minScore;
    }

    public BigDecimal getMaxScore() {
        return maxScore;
    }

    public void setMaxScore(BigDecimal maxScore) {
        this.maxScore = maxScore;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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
        return "AcademicDiagnosisRule{" +
                "id=" + id +
                ", ruleName='" + ruleName + '\'' +
                ", diagnosisLevel='" + diagnosisLevel + '\'' +
                ", minScore=" + minScore +
                ", maxScore=" + maxScore +
                ", description='" + description + '\'' +
                ", isDeleted=" + isDeleted +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                '}';
    }
}
