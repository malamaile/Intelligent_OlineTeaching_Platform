package com.iotp.enums;

/**
 * 角色枚举
 *
 * @author 杨雨洁
 * @since 2026-06-18
 */
public enum RoleEnum {

    STUDENT("STUDENT", "学生"),
    TEACHER("TEACHER", "教师"),
    ADMIN("ADMIN", "管理员");

    private final String code;
    private final String desc;

    RoleEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() { return code; }
    public String getDesc() { return desc; }

    /** 根据code获取枚举，找不到返回null */
    public static RoleEnum fromCode(String code) {
        for (RoleEnum e : values()) {
            if (e.code.equalsIgnoreCase(code)) return e;
        }
        return null;
    }
}
