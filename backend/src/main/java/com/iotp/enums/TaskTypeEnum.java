package com.iotp.enums;

/** 任务类型枚举：EXPERIMENT-实验, TRAINING-实训 */
public enum TaskTypeEnum {
    EXPERIMENT("EXPERIMENT", "实验"),
    TRAINING("TRAINING", "实训");
    private final String code;
    private final String desc;
    TaskTypeEnum(String code, String desc) { this.code = code; this.desc = desc; }
    public String getCode() { return code; }
    public String getDesc() { return desc; }
}
