package com.iotp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iotp.entity.SysUser;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

/**
 * SysUser Mapper 接口
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 物理删除用户（绕过 @TableLogic）
     */
    @Delete("DELETE FROM sys_user WHERE id = #{userId}")
    int physicalDelete(Long userId);
}
