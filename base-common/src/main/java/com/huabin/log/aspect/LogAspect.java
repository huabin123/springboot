package com.huabin.log.aspect;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

/**
 * @Author huabin
 * @DateTime 2023-01-10 13:41
 * @Desc
 */

@Component
@Aspect
@Slf4j
public class LogAspect {

    /**
     * 定义切点为controller层
     */
    @Pointcut("execution(* com.huabin..controller.*.*(..))")
    public void operationCell(){

    }

    @Around(value = "operationCell()")
    public Object doAround(ProceedingJoinPoint joinPoint) throws Throwable {
        String reqResult = "";

        try{
            Object result = joinPoint.proceed();
            reqResult = "操作成功";
            return result;
        } catch (Throwable e) {
            log.error(StrUtil.format("请求发生异常：{}", e.getMessage()));
            throw e;
        } finally {
            // 获取请求参数
            Object[] params = joinPoint.getArgs();
            JSONArray paramJsonArray = JSONUtil.parseArray(params);
            if (!paramJsonArray.isEmpty()) {
                log.info(paramJsonArray.toString());
            }
        }
    }

}
