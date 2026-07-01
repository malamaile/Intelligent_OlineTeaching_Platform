package com.iotp.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

/**
 * 系统操作日志实体
 */
@TableName("sys_operation_log")
public class SysOperationLog {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作模块 */
    private String module;

    /** 操作类型 */
    private String operation;

    /** 操作描述 */
    private String description;

    /** 操作人名称 */
    @TableField("operator_name")
    private String operatorName;

    /** 请求方式 */
    @TableField("request_method")
    private String requestMethod;

    /** 请求URL */
    @TableField("request_url")
    private String requestUrl;

    /** 结果状态 */
    @TableField("result_status")
    private Integer resultStatus;

    /** 错误信息 */
    @TableField("error_msg")
    private String errorMsg;

    /** 耗时(毫秒) */
    @TableField("duration_ms")
    private Long durationMs;

    /** 操作IP */
    private String ip;

    /** 创建时间，自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    // ==================== getter/setter ====================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public void setOperatorName(String operatorName) {
        this.operatorName = operatorName;
    }

    public String getRequestMethod() {
        return requestMethod;
    }

    public void setRequestMethod(String requestMethod) {
        this.requestMethod = requestMethod;
    }

    public String getRequestUrl() {
        return requestUrl;
    }

    public void setRequestUrl(String requestUrl) {
        this.requestUrl = requestUrl;
    }

    public Integer getResultStatus() {
        return resultStatus;
    }

    public void setResultStatus(Integer resultStatus) {
        this.resultStatus = resultStatus;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        return "SysOperationLog{" +
                "id=" + id +
                ", module='" + module + '\'' +
                ", operation='" + operation + '\'' +
                ", description='" + description + '\'' +
                ", operatorName='" + operatorName + '\'' +
                ", requestMethod='" + requestMethod + '\'' +
                ", requestUrl='" + requestUrl + '\'' +
                ", resultStatus=" + resultStatus +
                ", errorMsg='" + errorMsg + '\'' +
                ", durationMs=" + durationMs +
                ", ip='" + ip + '\'' +
                ", createTime=" + createTime +
                '}';
    }
}
