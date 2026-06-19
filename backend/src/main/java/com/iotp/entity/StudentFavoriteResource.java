package com.iotp.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

/**
 * 学生收藏资源实体
 */
@TableName("student_favorite_resource")
public class StudentFavoriteResource {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 学生ID */
    @TableField("student_id")
    private Long studentId;

    /** 资源ID */
    @TableField("resource_id")
    private Long resourceId;

    /** 创建时间，自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public Long getResourceId() {
        return resourceId;
    }

    public void setResourceId(Long resourceId) {
        this.resourceId = resourceId;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        return "StudentFavoriteResource{" +
                "id=" + id +
                ", studentId=" + studentId +
                ", resourceId=" + resourceId +
                ", createTime=" + createTime +
                '}';
    }
}
