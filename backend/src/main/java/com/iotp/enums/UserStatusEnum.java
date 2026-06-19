package com.iotp.enums;

/**
 * 用户账号状态枚举
 */
public enum UserStatusEnum {

    ACTIVE("ACTIVE", "正常"),
    FROZEN("FROZEN", "已冻结"),
    LOCKED("LOCKED", "已锁定（密码错误超限）");

    private final String code;
    private final String desc;

    UserStatusEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() { return code; }
    public String getDesc() { return desc; }
}
