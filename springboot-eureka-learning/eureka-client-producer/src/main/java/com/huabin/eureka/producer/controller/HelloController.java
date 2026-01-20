package com.huabin.eureka.producer.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Hello Controller
 * 提供简单的REST接口
 * 
 * @author huabin
 * @date 2024-01-19
 */
@Slf4j
@RestController
@RequestMapping("/hello")
public class HelloController {

    @Value("${server.port}")
    private String port;

    @Value("${spring.application.name}")
    private String applicationName;

    /**
     * Hello接口
     * 
     * @param name 名称
     * @return 问候语
     */
    @GetMapping("/{name}")
    public String hello(@PathVariable String name) {
        String message = String.format("Hello %s! 来自服务: %s, 端口: %s, 时间: %s",
                name,
                applicationName,
                port,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        
        log.info("收到请求，返回: {}", message);
        return message;
    }

    /**
     * 获取服务信息
     * 
     * @return 服务信息
     */
    @GetMapping("/info")
    public String info() {
        return String.format("服务名称: %s, 端口: %s", applicationName, port);
    }
}
