package com.huabin.log4j.test;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * Log4j2 兼容性测试
 * 
 * @author huabin
 * @date 2026-02-03
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class Log4jCompatibilityTest {

    @Test
    public void testLog4jVersion() {
        // 获取 Log4j2 版本
        String log4jVersion = org.apache.logging.log4j.LogManager.class.getPackage().getImplementationVersion();
        log.info("Log4j2 版本：{}", log4jVersion);
        
        // 验证版本是否为 2.25.3
        assert log4jVersion != null;
        assert log4jVersion.startsWith("2.25");
        
        log.info("✅ Log4j2 版本验证通过");
    }

    @Test
    public void testLogLevels() {
        log.trace("TRACE 级别日志测试");
        log.debug("DEBUG 级别日志测试");
        log.info("INFO 级别日志测试");
        log.warn("WARN 级别日志测试");
        log.error("ERROR 级别日志测试");
        
        log.info("✅ 日志级别测试通过");
    }

    @Test
    public void testLogWithParameters() {
        String name = "张三";
        int age = 25;
        
        log.info("用户信息：姓名={}, 年龄={}", name, age);
        log.info("✅ 参数化日志测试通过");
    }

    @Test
    public void testLogWithException() {
        try {
            int result = 10 / 0;
        } catch (Exception e) {
            log.error("发生异常", e);
            log.info("✅ 异常日志测试通过");
        }
    }
}
