package com.iotp.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

/**
 * 系统审核日志实体
 */
@TableName("sys_audit_log")
public class SysAuditLog {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务类型 */
    @TableField("biz_type")
    private String bizType;

    /** 业务ID */
    @TableField("biz_id")
    private Long bizId;

    /** 业务名称 */
    @TableField("biz_name")
    private String bizName;

    /** 操作 */
    private String action;

    /** 操作人ID */
    @TableField("operator_id")
    private Long operatorId;

    /** 操作人名称 */
    @TableField("operator_name")
    private String operatorName;

    /** 备注 */
    private String comment;

    /** 操作前状态 */
    @TableField("before_status")
    private String beforeStatus;

    /** 操作后状态 */
    @TableField("after_status")
    private String afterStatus;

    /** 创建时间，自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    public Long getBizId() {
        return bizId;
    }

    public void setBizId(Long bizId) {
        this.bizId = bizId;
    }

    public String getBizName() {
        return bizName;
    }

    public void setBizName(String bizName) {
        this.bizName = bizName;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public void setOperatorName(String operatorName) {
        this.operatorName = operatorName;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getBeforeStatus() {
        return beforeStatus;
    }

    public void setBeforeStatus(String beforeStatus) {
        this.beforeStatus = beforeStatus;
    }

    public String getAfterStatus() {
        return afterStatus;
    }

    public void setAfterStatus(String afterStatus) {
        this.afterStatus = afterStatus;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        return "SysAuditLog{" +
                "id=" + id +
                ", bizType='" + bizType + '\'' +
                ", bizId=" + bizId +
                ", bizName='" + bizName + '\'' +
                ", action='" + action + '\'' +
                ", operatorId=" + operatorId +
                ", operatorName='" + operatorName + '\'' +
                ", comment='" + comment + '\'' +
                ", beforeStatus='" + beforeStatus + '\'' +
                ", afterStatus='" + afterStatus + '\'' +
                ", createTime=" + createTime +
                '}';
    }
}
