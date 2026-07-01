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

    /** 操作人ID */
    @TableField("operator_id")
    private Long operatorId;

    /** 操作人姓名 */
    @TableField("operator_name")
    private String operatorName;

    /** 请求方法 */
    @TableField("request_method")
    private String requestMethod;

    /** 请求URL */
    @TableField("request_url")
    private String requestUrl;

    /** 请求参数 */
    @TableField("request_params")
    private String requestParams;

    /** 执行结果 1成功 0失败 */
    @TableField("result_status")
    private Integer resultStatus;

    /** 错误信息 */
    @TableField("error_msg")
    private String errorMsg;

    /** 耗时(ms) */
    @TableField("duration_ms")
    private Long durationMs;

    /** 操作IP */
    private String ip;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    // ======== getters & setters ========
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }

    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }

    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }

    public String getRequestMethod() { return requestMethod; }
    public void setRequestMethod(String requestMethod) { this.requestMethod = requestMethod; }

    public String getRequestUrl() { return requestUrl; }
    public void setRequestUrl(String requestUrl) { this.requestUrl = requestUrl; }

    public String getRequestParams() { return requestParams; }
    public void setRequestParams(String requestParams) { this.requestParams = requestParams; }

    public Integer getResultStatus() { return resultStatus; }
    public void setResultStatus(Integer resultStatus) { this.resultStatus = resultStatus; }

    public String getErrorMsg() { return errorMsg; }
    public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }

    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }

    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
