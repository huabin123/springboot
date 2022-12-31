package com.huabin.ratelimiter.controller;

import com.huabin.ratelimiter.common.annotation.RateLimiterSemaphore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * @Author huabin
 * @DateTime 2022-12-29 08:36
 * @Desc
 */

@Controller
@Slf4j
public class RateLimiterController {

    @GetMapping(value = "/test")
    @ResponseBody
    @RateLimiterSemaphore
    public String rateLimiterTest() throws InterruptedException {
        log.info("业务执行");
        Thread.sleep(2000);
        return "hello world";
    }

}
