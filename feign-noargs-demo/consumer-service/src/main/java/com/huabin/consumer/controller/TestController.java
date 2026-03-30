package com.huabin.consumer.controller;

import com.huabin.consumer.dto.UserDTO;
import com.huabin.consumer.dto.UserDTOFixed;
import com.huabin.consumer.dto.UserDTOLombok;
import com.huabin.consumer.feign.UserFeignClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 测试控制器 - 服务消费者
 * 
 * 通过此控制器测试 Feign 调用的不同场景
 */
@RestController
@RequestMapping("/test")
public class TestController {

    private static final Logger log = LoggerFactory.getLogger(TestController.class);

    @Autowired
    private UserFeignClient userFeignClient;

    /**
     * ❌ 测试问题场景：调用返回没有无参构造方法的 DTO
     * 
     * 访问: http://localhost:8082/test/problem/1
     * 
     * 预期结果：抛出异常
     * 错误信息示例：
     * - JSON parse error: Cannot construct instance of `com.huabin.consumer.dto.UserDTO`
     * - no Creators, like default constructor, exist
     */
    @GetMapping("/problem/{id}")
    public Map<String, Object> testProblem(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        try {
            log.info("Consumer: 开始测试问题场景 - 调用 /api/users/problem/{}", id);
            UserDTO user = userFeignClient.getUserWithProblem(id);
            log.info("Consumer: ❌ 不应该执行到这里！获取到用户: {}", user);
            result.put("success", true);
            result.put("data", user);
            result.put("message", "调用成功（不应该发生）");
        } catch (Exception e) {
            log.error("Consumer: ✅ 预期的错误发生了！错误信息: {}", e.getMessage());
            result.put("success", false);
            result.put("error", e.getClass().getSimpleName());
            result.put("message", e.getMessage());
            result.put("explanation", "因为 UserDTO 没有无参构造方法，JSON 反序列化失败");
        }
        return result;
    }

    /**
     * ✅ 测试修复场景：调用返回有无参构造方法的 DTO
     * 
     * 访问: http://localhost:8082/test/fixed/1
     * 
     * 预期结果：调用成功，正常返回数据
     */
    @GetMapping("/fixed/{id}")
    public Map<String, Object> testFixed(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        try {
            log.info("Consumer: 开始测试修复场景 - 调用 /api/users/fixed/{}", id);
            UserDTOFixed user = userFeignClient.getUserFixed(id);
            log.info("Consumer: ✅ 调用成功！获取到用户: {}", user);
            result.put("success", true);
            result.put("data", user);
            result.put("message", "调用成功");
            result.put("explanation", "因为 UserDTOFixed 有无参构造方法，JSON 反序列化成功");
        } catch (Exception e) {
            log.error("Consumer: ❌ 不应该发生错误！错误信息: {}", e.getMessage());
            result.put("success", false);
            result.put("error", e.getClass().getSimpleName());
            result.put("message", e.getMessage());
        }
        return result;
    }

    /**
     * ✅ 测试 Lombok 场景：调用返回使用 Lombok 的 DTO
     * 
     * 访问: http://localhost:8082/test/lombok/1
     * 
     * 预期结果：调用成功，正常返回数据
     */
    @GetMapping("/lombok/{id}")
    public Map<String, Object> testLombok(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        try {
            log.info("Consumer: 开始测试 Lombok 场景 - 调用 /api/users/lombok/{}", id);
            UserDTOLombok user = userFeignClient.getUserLombok(id);
            log.info("Consumer: ✅ 调用成功！获取到用户: {}", user);
            result.put("success", true);
            result.put("data", user);
            result.put("message", "调用成功");
            result.put("explanation", "因为 UserDTOLombok 使用 @NoArgsConstructor 生成了无参构造方法");
        } catch (Exception e) {
            log.error("Consumer: ❌ 不应该发生错误！错误信息: {}", e.getMessage());
            result.put("success", false);
            result.put("error", e.getClass().getSimpleName());
            result.put("message", e.getMessage());
        }
        return result;
    }

    /**
     * 健康检查接口
     */
    @GetMapping("/health")
    public String health() {
        return "Consumer Service is running!";
    }
}
