package com.huabin.log4j.test;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Spring Boot 2.2.2.RELEASE + Log4j2 2.25.3 兼容性测试
 * 
 * @author huabin
 * @date 2026-02-03
 */
@Slf4j
@SpringBootApplication(exclude = {
    org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class,
    org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration.class
})
@RestController
public class Log4jCompatibilityTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(Log4jCompatibilityTestApplication.class, args);
        
        // 测试日志输出
        log.info("========================================");
        log.info("Spring Boot 2.2.2.RELEASE 启动成功！");
        log.info("Log4j2 版本：2.25.3");
        log.info("========================================");
        
        // 测试不同级别的日志
        log.trace("这是 TRACE 级别日志");
        log.debug("这是 DEBUG 级别日志");
        log.info("这是 INFO 级别日志");
        log.warn("这是 WARN 级别日志");
        log.error("这是 ERROR 级别日志");
    }

    @GetMapping("/test")
    public String test() {
        log.info("收到测试请求");
        return "Spring Boot 2.2.2.RELEASE + Log4j2 2.25.3 兼容性测试成功！";
    }

    @GetMapping("/log-test")
    public String logTest() {
        log.trace("TRACE 级别日志测试");
        log.debug("DEBUG 级别日志测试");
        log.info("INFO 级别日志测试");
        log.warn("WARN 级别日志测试");
        log.error("ERROR 级别日志测试");
        
        return "日志测试完成，请查看控制台输出";
    }

    @GetMapping("/version")
    public String version() {
        String springBootVersion = SpringApplication.class.getPackage().getImplementationVersion();
        String log4jVersion = org.apache.logging.log4j.LogManager.class.getPackage().getImplementationVersion();
        
        log.info("Spring Boot 版本：{}", springBootVersion);
        log.info("Log4j2 版本：{}", log4jVersion);
        
        return String.format("Spring Boot: %s, Log4j2: %s", springBootVersion, log4jVersion);
    }
}
