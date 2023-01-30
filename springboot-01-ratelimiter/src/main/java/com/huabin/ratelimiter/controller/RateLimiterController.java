package com.huabin.ratelimiter.controller;

import com.huabin.exception.AjaxResult;
import com.huabin.exception.BusinessException;
import com.huabin.exception.constant.CommonCode;
import com.huabin.ratelimiter.bean.request.RateLimiterTestRequest;
import com.huabin.ratelimiter.bean.resp.RateLimiterTestResp;
import com.huabin.ratelimiter.common.annotation.RateLimiterSemaphore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * @Author huabin
 * @DateTime 2022-12-29 08:36
 * @Desc
 */

@RestController
@Slf4j
public class RateLimiterController {

    @PostMapping(value = "/test")
    @RateLimiterSemaphore
    public RateLimiterTestResp rateLimiterTest(@RequestBody RateLimiterTestRequest request) throws InterruptedException {
        log.info("业务执行");
        Thread.sleep(2000);
//        return AjaxResult.ok(RateLimiterTestResp.builder().name("test").build());
        return RateLimiterTestResp.builder().name("123").build();
//        throw new BusinessException(CommonCode.COMMON_ERROR);
    }

}
