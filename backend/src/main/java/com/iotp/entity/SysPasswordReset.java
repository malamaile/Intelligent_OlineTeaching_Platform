package com.iotp.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

/**
 * 密码重置实体
 */
@TableName("sys_password_reset")
public class SysPasswordReset {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    @TableField("user_id")
    private Long userId;

    /** 重置令牌 */
    @TableField("reset_token")
    private String resetToken;

    /** 过期时间 */
    @TableField("expire_time")
    private LocalDateTime expireTime;

    /** 是否已使用 */
    @TableField("is_used")
    private Integer isUsed;

    /** 创建时间，自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getResetToken() {
        return resetToken;
    }

    public void setResetToken(String resetToken) {
        this.resetToken = resetToken;
    }

    public LocalDateTime getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(LocalDateTime expireTime) {
        this.expireTime = expireTime;
    }

    public Integer getIsUsed() {
        return isUsed;
    }

    public void setIsUsed(Integer isUsed) {
        this.isUsed = isUsed;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        return "SysPasswordReset{" +
                "id=" + id +
                ", userId=" + userId +
                ", resetToken='" + resetToken + '\'' +
                ", expireTime=" + expireTime +
                ", isUsed=" + isUsed +
                ", createTime=" + createTime +
                '}';
    }
}
