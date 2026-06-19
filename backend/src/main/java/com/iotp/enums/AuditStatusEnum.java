package com.iotp.enums;

/**
 * 审核状态枚举
 */
public enum AuditStatusEnum {
    PENDING("PENDING", "待审核"),
    APPROVED("APPROVED", "审核通过"),
    REJECTED("REJECTED", "审核驳回");

    private final String code;
    private final String desc;
    AuditStatusEnum(String code, String desc) { this.code = code; this.desc = desc; }
    public String getCode() { return code; }
    public String getDesc() { return desc; }
}
