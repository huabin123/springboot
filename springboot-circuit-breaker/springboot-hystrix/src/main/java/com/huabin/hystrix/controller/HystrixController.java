package com.huabin.hystrix.controller;

import com.huabin.hystrix.bean.request.HystrixTestRequest;
import com.huabin.hystrix.bean.resp.HystrixTestResp;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author huabin
 * @DateTime 2022-12-29 08:36
 * @Desc
 */

@RestController
@Slf4j
public class HystrixController {

    @PostMapping(value = "/test")
    @HystrixCommand(
            fallbackMethod = "fallBackMethod",
            commandProperties = {
                    @HystrixProperty(name = "circuitBreaker.enabled", value = "true")
            }
    )
    public HystrixTestResp hystrixTest(@RequestBody HystrixTestRequest request) {
        log.info("业务执行");
        return HystrixTestResp.builder().name("test").build();
//        throw new MyHystrixBadRequestException(e.getMessage(),e,CommonCode.COMMON_ERROR.getCode());
    }

    public HystrixTestResp fallBackMethod(HystrixTestRequest request) {
        log.info("业务执行失败，执行fallBackMethod");
        return HystrixTestResp.builder().name("业务执行失败，执行fallBackMethod").build();
    }


}
