package com.huabin.ratelimiter.common.aspect;

import cn.hutool.core.util.StrUtil;
import com.huabin.ratelimiter.common.annotation.RateLimiterSemaphore;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @Author huabin
 * @DateTime 2022-12-29 09:24
 * @Desc
 */

@Component
@Aspect
@Slf4j
public class SemaphoreRateLimiterAspect {

    private static final ConcurrentHashMap<String, Semaphore> semaphoreObjectCacheMap = new ConcurrentHashMap<>();

    @Pointcut("@annotation(com.huabin.ratelimiter.common.annotation.RateLimiterSemaphore)")
    public void rateLimiter() {
    }

    @Around(value = "rateLimiter()", argNames = "joinPoint")
    public Object doAround(ProceedingJoinPoint joinPoint) throws Throwable {
        // 获取注解参数
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        RateLimiterSemaphore annotation = methodSignature.getMethod().getAnnotation(RateLimiterSemaphore.class);

        // 获取当前全类名
        String key = joinPoint.getTarget().getClass().getName() + "." + joinPoint.getSignature().getName();
        log.info(StrUtil.format("key:{}",key));

        // 获取配置的并发数
        int maxNum = annotation.qps();

        Semaphore semaphore = getSemaphore(key, maxNum);

        AtomicBoolean releaseFlag = new AtomicBoolean(true);
        try {
            releaseFlag.set(semaphore.tryAcquire(1, TimeUnit.SECONDS));
            if (!releaseFlag.get()) {
                throw new Exception("并发数已达上限");
            }
            return joinPoint.proceed();
        } finally {
            if (releaseFlag.get()) {
                semaphore.release();
            }
        }
    }

    synchronized private Semaphore getSemaphore(String key, int maxNum) {
        Semaphore semaphore;
        if (semaphoreObjectCacheMap.get(key) == null) {
            log.info(StrUtil.format("最大并发数：{}", maxNum));
            semaphore = new Semaphore(maxNum);
            semaphoreObjectCacheMap.put(key, semaphore);
        } else {
            semaphore = semaphoreObjectCacheMap.get(key);
        }
        return semaphore;
    }

}
