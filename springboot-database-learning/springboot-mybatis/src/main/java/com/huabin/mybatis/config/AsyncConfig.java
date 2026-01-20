package com.huabin.mybatis.config;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @Author huabin
 * @DateTime 2025-12-29
 * @Desc 异步配置类
 * 
 * 关键配置说明：
 * 1. @EnableAsync：启用Spring异步方法支持
 * 2. 配置线程池：控制异步任务的并发数
 * 3. 异常处理：处理异步方法中未捕获的异常
 * 
 * 注意事项：
 * - 线程池大小要与数据库连接池匹配
 * - 避免线程池过大导致数据库连接耗尽
 * - 建议：maxPoolSize <= datasource.hikari.maximum-pool-size
 */
@Configuration
@EnableAsync  // 启用异步方法支持
public class AsyncConfig implements AsyncConfigurer {

    /**
     * 自定义异步线程池
     * 
     * 线程池参数说明：
     * - corePoolSize: 核心线程数，即使空闲也会保持
     * - maxPoolSize: 最大线程数
     * - queueCapacity: 队列容量，超过核心线程数的任务会进入队列
     * - keepAliveTime: 非核心线程的空闲存活时间（默认60秒）
     * 
     * 执行流程：
     * 1. 任务数 <= corePoolSize：创建新线程执行
     * 2. 任务数 > corePoolSize：任务进入队列
     * 3. 队列满 && 线程数 < maxPoolSize：创建新线程
     * 4. 队列满 && 线程数 = maxPoolSize：执行拒绝策略
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 核心线程数：根据CPU核心数设置
        // CPU密集型：核心数 + 1
        // IO密集型（数据库操作）：核心数 * 2
        int processors = Runtime.getRuntime().availableProcessors();
        executor.setCorePoolSize(processors * 2);
        
        // 最大线程数：不要超过数据库连接池大小
        executor.setMaxPoolSize(processors * 4);
        
        // 队列容量：缓冲等待的任务
        executor.setQueueCapacity(200);
        
        // 线程名称前缀：便于日志追踪
        executor.setThreadNamePrefix("mybatis-async-");
        
        // 线程空闲时间（秒）
        executor.setKeepAliveSeconds(60);
        
        // 拒绝策略：队列满且线程数达到最大时的处理策略
        // CallerRunsPolicy：由调用线程执行（降级为同步）
        // AbortPolicy：抛出异常（默认）
        // DiscardPolicy：直接丢弃
        // DiscardOldestPolicy：丢弃队列中最老的任务
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        
        // 等待所有任务完成后再关闭线程池
        executor.setWaitForTasksToCompleteOnShutdown(true);
        
        // 等待时间（秒）
        executor.setAwaitTerminationSeconds(60);
        
        // 初始化
        executor.initialize();
        
        return executor;
    }

    /**
     * 获取异步执行器
     */
    @Override
    public Executor getAsyncExecutor() {
        return taskExecutor();
    }

    /**
     * 异步方法异常处理器
     * 
     * 处理异步方法中未被捕获的异常
     * 注意：只对返回void的异步方法有效
     * 返回Future的方法需要在调用future.get()时处理异常
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, params) -> {
            System.err.println("==========================");
            System.err.println("异步方法执行异常");
            System.err.println("方法名: " + method.getName());
            System.err.println("类名: " + method.getDeclaringClass().getName());
            System.err.println("参数: ");
            for (Object param : params) {
                System.err.println("  - " + param);
            }
            System.err.println("异常信息: " + throwable.getMessage());
            System.err.println("==========================");
            throwable.printStackTrace();
        };
    }

    /**
     * 可选：配置第二个线程池用于不同类型的异步任务
     * 
     * 使用场景：
     * - taskExecutor: 数据库操作（IO密集型）
     * - cpuTaskExecutor: CPU密集型计算
     */
    @Bean(name = "cpuTaskExecutor")
    public Executor cpuTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        int processors = Runtime.getRuntime().availableProcessors();
        executor.setCorePoolSize(processors);
        executor.setMaxPoolSize(processors * 2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("cpu-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        
        return executor;
    }
}
