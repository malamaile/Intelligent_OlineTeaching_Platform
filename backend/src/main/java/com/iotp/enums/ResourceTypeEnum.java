package com.iotp.enums;

/** 资源类型枚举 */
public enum ResourceTypeEnum {
    COURSEWARE("COURSEWARE", "课件"),
    EXERCISE("EXERCISE", "习题"),
    VIDEO("VIDEO", "视频"),
    DOCUMENT("DOCUMENT", "文档"),
    OTHER("OTHER", "其他");
    private final String code;
    private final String desc;
    ResourceTypeEnum(String code, String desc) { this.code = code; this.desc = desc; }
    public String getCode() { return code; }
    public String getDesc() { return desc; }
}
