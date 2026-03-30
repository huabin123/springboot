package com.huabin.provider.controller;

import com.huabin.provider.dto.UserDTO;
import com.huabin.provider.dto.UserDTOFixed;
import com.huabin.provider.dto.UserDTOLombok;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户控制器 - 服务提供者
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    /**
     * ❌ 问题接口：返回没有无参构造方法的 DTO
     * Feign 调用此接口会失败
     */
    @GetMapping("/problem/{id}")
    public UserDTO getUserWithProblem(@PathVariable Long id) {
        log.info("Provider: 调用问题接口 /api/users/problem/{}", id);
        UserDTO user = new UserDTO(id, "张三", "zhangsan@example.com", 25);
        log.info("Provider: 返回数据 {}", user);
        return user;
    }

    /**
     * ✅ 修复接口：返回有无参构造方法的 DTO
     * Feign 调用此接口会成功
     */
    @GetMapping("/fixed/{id}")
    public UserDTOFixed getUserFixed(@PathVariable Long id) {
        log.info("Provider: 调用修复接口 /api/users/fixed/{}", id);
        UserDTOFixed user = new UserDTOFixed(id, "李四", "lisi@example.com", 30);
        log.info("Provider: 返回数据 {}", user);
        return user;
    }

    /**
     * ✅ Lombok 版本接口：使用 Lombok 注解的 DTO
     * Feign 调用此接口会成功
     */
    @GetMapping("/lombok/{id}")
    public UserDTOLombok getUserLombok(@PathVariable Long id) {
        log.info("Provider: 调用 Lombok 接口 /api/users/lombok/{}", id);
        UserDTOLombok user = new UserDTOLombok(id, "王五", "wangwu@example.com", 28);
        log.info("Provider: 返回数据 {}", user);
        return user;
    }

    /**
     * 健康检查接口
     */
    @GetMapping("/health")
    public String health() {
        return "Provider Service is running!";
    }
}
