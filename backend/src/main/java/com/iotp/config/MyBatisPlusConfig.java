package com.iotp.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * MyBatis-Plus 配置
 * <p>配置分页插件、SQL 性能分析插件等，同时启用声明式事务管理。</p>
 */
@Configuration
@MapperScan("com.iotp.mapper")
@EnableTransactionManagement
public class MyBatisPlusConfig {

    /**
     * MyBatis-Plus 插件容器（推荐方式：将所有插件放入此拦截器）
     * <p>当前注册的插件：</p>
     * <ul>
     *   <li>分页插件（PaginationInnerInterceptor）—— 自动处理分页查询</li>
     * </ul>
     *
     * @return MybatisPlusInterceptor 实例
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 1. 分页插件（数据库类型为 MySQL）
        //    支持 pageSize 和 current 参数的自动映射
        PaginationInnerInterceptor paginationInterceptor = new PaginationInnerInterceptor(DbType.MYSQL);
        // 设置超过最大页数后是否返回空页（true=返回空页，false=继续查询）
        paginationInterceptor.setOverflow(false);
        // 单页最大条数限制，防止恶意查询
        paginationInterceptor.setMaxLimit(500L);
        interceptor.addInnerInterceptor(paginationInterceptor);

        return interceptor;
    }

}
