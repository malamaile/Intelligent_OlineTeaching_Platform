package com.iotp.entity;

import com.baomidou.mybatisplus.annotation.*;

/**
 * 课程计划与行政班级关联实体（多对多）
 */
@TableName("course_plan_class")
public class CoursePlanClass {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("course_plan_id")
    private Long coursePlanId;

    @TableField("class_id")
    private Long classId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCoursePlanId() { return coursePlanId; }
    public void setCoursePlanId(Long coursePlanId) { this.coursePlanId = coursePlanId; }
    public Long getClassId() { return classId; }
    public void setClassId(Long classId) { this.classId = classId; }
}
