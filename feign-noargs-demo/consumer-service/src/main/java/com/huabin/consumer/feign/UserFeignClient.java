package com.huabin.consumer.feign;

import com.huabin.consumer.dto.UserDTO;
import com.huabin.consumer.dto.UserDTOFixed;
import com.huabin.consumer.dto.UserDTOLombok;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 用户服务 Feign 客户端
 * 
 * 通过 Feign 调用 Provider Service 的接口
 */
@FeignClient(
    name = "provider-service",
    url = "http://localhost:8081"
)
public interface UserFeignClient {

    /**
     * ❌ 问题接口：调用返回没有无参构造方法的 DTO 的接口
     * 
     * 预期结果：调用失败
     * 错误信息：JSON parse error 或 Cannot construct instance
     */
    @GetMapping("/api/users/problem/{id}")
    UserDTO getUserWithProblem(@PathVariable("id") Long id);

    /**
     * ✅ 修复接口：调用返回有无参构造方法的 DTO 的接口
     * 
     * 预期结果：调用成功
     */
    @GetMapping("/api/users/fixed/{id}")
    UserDTOFixed getUserFixed(@PathVariable("id") Long id);

    /**
     * ✅ Lombok 版本接口：调用返回使用 Lombok 的 DTO 的接口
     * 
     * 预期结果：调用成功
     */
    @GetMapping("/api/users/lombok/{id}")
    UserDTOLombok getUserLombok(@PathVariable("id") Long id);
}
