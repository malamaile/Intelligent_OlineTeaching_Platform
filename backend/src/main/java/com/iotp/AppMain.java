package com.iotp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * IOTP 智能化在线教学支持服务平台 — 启动类
 *
 * @author 杨雨洁
 * @since 2026-06-18
 */
@SpringBootApplication
public class AppMain {

    public static void main(String[] args) {
        SpringApplication.run(AppMain.class, args);
        System.out.println("========================================");
        System.out.println("  IOTP 服务启动成功！");
        System.out.println("  API 地址: http://localhost:8080/api/v1");
        System.out.println("========================================");
    }

}
