package com.iotp.enums;

/** 资源可见范围 */
public enum ResourceScopeEnum {
    CLASS_ONLY("CLASS_ONLY", "仅本班"),
    DEPARTMENT_WIDE("DEPARTMENT_WIDE", "本院系"),
    SCHOOL_WIDE("SCHOOL_WIDE", "全校");

    private final String code;
    private final String desc;
    ResourceScopeEnum(String code, String desc) { this.code = code; this.desc = desc; }
    public String getCode() { return code; }
    public String getDesc() { return desc; }
}
