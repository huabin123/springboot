package com.huabin.log.annotation;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface MyLog {

    /**
     * 功能模块
     * @return
     */
    String moduleId() default "";

    /**
     * 请求链接
     * @return
     */
    String reqUrl() default "";

    /**
     * 操作名称
     * @return
     */
    String optName() default "";
}
