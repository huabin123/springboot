package com.huabin.ratelimiter.common.annotation;


import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RateLimiterSemaphore {

    int qps() default 2;

}
