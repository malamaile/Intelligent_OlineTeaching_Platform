package com.iotp.config;

import com.iotp.security.LoginInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置
 * <p>注册拦截器、配置跨域（CORS）、静态资源映射等。</p>
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private LoginInterceptor loginInterceptor;

    // ==================== 拦截器注册 ====================

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor)
                // 拦截所有请求（context-path 外的实际路径）
                .addPathPatterns("/**")
                // 放行不需要登录的接口
                .excludePathPatterns(
                        "/auth/login",                         // 登录
                        "/auth/register",                      // 注册
                        "/auth/forgot-password/verify",        // 忘记密码-验证身份
                        "/auth/forgot-password/reset"          // 忘记密码-重置密码
                );
    }

    // ==================== 跨域配置（CORS） ====================

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                // 允许所有来源（开发阶段；生产环境请限定具体域名）
                .allowedOriginPatterns("*")
                // 允许所有 HTTP 方法
                .allowedMethods("GET", "HEAD", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                // 允许所有请求头
                .allowedHeaders("*")
                // 允许携带认证信息（Cookie、Authorization 头等）
                .allowCredentials(true)
                // 预检请求缓存时间（秒），减少 OPTIONS 请求次数
                .maxAge(3600);
    }

    // ==================== 静态资源映射 ====================

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Swagger / Knife4j 静态资源（如果后续集成可自动生效）
        registry.addResourceHandler("/doc.html")
                .addResourceLocations("classpath:/META-INF/resources/");

        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/");
    }

}
