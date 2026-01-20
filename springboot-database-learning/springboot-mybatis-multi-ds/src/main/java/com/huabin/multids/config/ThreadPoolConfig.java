package com.huabin.multids.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @Author huabin
 * @DateTime 2025-12-29
 * @Desc 线程池配置类
 * 
 * 说明：
 * 1. 用于异步执行批量创建产品任务
 * 2. 配置合理的线程池参数，避免资源耗尽
 * 3. 配置拒绝策略，处理任务队列满的情况
 */
@Configuration
@EnableAsync
public class ThreadPoolConfig {

    private static final Logger logger = LoggerFactory.getLogger(ThreadPoolConfig.class);

    /**
     * 产品批量创建任务线程池
     * 
     * 使用 ThreadPoolExecutor 直接创建，参数说明：
     * 
     * 1. corePoolSize（核心线程数）：
     *    - IO密集型任务：CPU核心数 * 2
     *    - 这些线程会一直存活，即使空闲也不会被回收
     * 
     * 2. maximumPoolSize（最大线程数）：
     *    - 设置为核心线程数的2倍
     *    - 当队列满时，会创建新线程直到达到最大线程数
     * 
     * 3. keepAliveTime（非核心线程存活时间）：
     *    - 60秒，非核心线程空闲超过此时间会被回收
     * 
     * 4. workQueue（工作队列）：
     *    - 使用 LinkedBlockingQueue，容量为 500
     *    - 有界队列，防止内存溢出
     *    - 当核心线程都在忙时，新任务会进入队列
     * 
     * 5. threadFactory（线程工厂）：
     *    - 自定义线程名称，便于问题排查
     *    - 设置为守护线程，JVM退出时自动结束
     * 
     * 6. rejectedExecutionHandler（拒绝策略）：
     *    - AbortPolicy：直接抛出 RejectedExecutionException 异常
     *    - 优点：明确告知任务被拒绝，便于上层处理
     *    - 缺点：需要调用方捕获异常并处理
     *    - 适用场景：需要精确控制任务提交失败的情况
     * 
     * 线程池执行流程：
     * 1. 任务提交时，如果核心线程未满，创建核心线程执行
     * 2. 核心线程满了，任务进入队列
     * 3. 队列满了，创建非核心线程执行（直到最大线程数）
     * 4. 最大线程数也满了，执行拒绝策略
     */
    @Bean(name = "productCreateExecutor")
    public ThreadPoolExecutor productCreateExecutor() {
        // 获取CPU核心数
        int cpuCores = Runtime.getRuntime().availableProcessors();
        
        // 核心线程数：IO密集型任务，设置为CPU核心数的2倍
        int corePoolSize = cpuCores * 2;
        
        // 最大线程数：核心线程数的2倍
        int maximumPoolSize = corePoolSize * 2;
        
        // 非核心线程存活时间：60秒
        long keepAliveTime = 60L;
        
        // 工作队列：有界队列，容量500
        BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>(500);
        
        // 线程工厂：自定义线程名称
        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);
            
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "product-create-" + threadNumber.getAndIncrement());
                // 设置为非守护线程，确保任务执行完成
                thread.setDaemon(false);
                // 设置线程优先级为正常
                thread.setPriority(Thread.NORM_PRIORITY);
                return thread;
            }
        };
        
        // 拒绝策略：直接抛出异常
        RejectedExecutionHandler rejectedHandler = new ThreadPoolExecutor.AbortPolicy();
        
        // 创建线程池
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
            corePoolSize,
            maximumPoolSize,
            keepAliveTime,
            TimeUnit.SECONDS,
            workQueue,
            threadFactory,
            rejectedHandler
        );
        
        // 允许核心线程超时（可选，如果希望核心线程也能被回收）
        // executor.allowCoreThreadTimeOut(true);
        
        logger.info("========================================");
        logger.info("产品创建线程池初始化成功（ThreadPoolExecutor）");
        logger.info("CPU核心数: {}", cpuCores);
        logger.info("核心线程数: {}", corePoolSize);
        logger.info("最大线程数: {}", maximumPoolSize);
        logger.info("队列容量: {}", workQueue.remainingCapacity());
        logger.info("非核心线程存活时间: {}秒", keepAliveTime);
        logger.info("拒绝策略: AbortPolicy（抛出异常）");
        logger.info("========================================");
        
        return executor;
    }

    /**
     * 通用异步任务线程池（可选）
     * 
     * 用于其他异步任务，与产品创建任务隔离
     */
    @Bean(name = "asyncExecutor")
    public ThreadPoolExecutor asyncExecutor() {
        int cpuCores = Runtime.getRuntime().availableProcessors();
        int corePoolSize = cpuCores;
        int maximumPoolSize = cpuCores * 2;
        long keepAliveTime = 60L;
        BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>(100);
        
        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);
            
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "async-" + threadNumber.getAndIncrement());
                thread.setDaemon(false);
                thread.setPriority(Thread.NORM_PRIORITY);
                return thread;
            }
        };
        
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
            corePoolSize,
            maximumPoolSize,
            keepAliveTime,
            TimeUnit.SECONDS,
            workQueue,
            threadFactory,
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
        
        logger.info("通用异步线程池初始化成功（ThreadPoolExecutor）");
        logger.info("核心线程数: {}, 最大线程数: {}, 队列容量: {}", 
                   corePoolSize, maximumPoolSize, workQueue.remainingCapacity());
        
        return executor;
    }

    /**
     * 线程池关闭钩子
     * 
     * 应用关闭时，优雅地关闭线程池
     */
    @Bean
    public ThreadPoolShutdownHook threadPoolShutdownHook(ThreadPoolExecutor productCreateExecutor,
                                                          ThreadPoolExecutor asyncExecutor) {
        return new ThreadPoolShutdownHook(productCreateExecutor, asyncExecutor);
    }

    /**
     * 线程池关闭钩子类
     */
    private static class ThreadPoolShutdownHook {
        private final ThreadPoolExecutor[] executors;

        public ThreadPoolShutdownHook(ThreadPoolExecutor... executors) {
            this.executors = executors;
            
            // 注册JVM关闭钩子
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("开始关闭线程池...");
                
                for (ThreadPoolExecutor executor : executors) {
                    if (executor != null && !executor.isShutdown()) {
                        // 停止接收新任务
                        executor.shutdown();
                        
                        try {
                            // 等待已提交的任务执行完成，最多等待60秒
                            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                                // 超时后强制关闭
                                executor.shutdownNow();
                                
                                // 再等待一段时间
                                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                                    logger.error("线程池无法正常关闭");
                                }
                            }
                            logger.info("线程池已关闭");
                        } catch (InterruptedException e) {
                            executor.shutdownNow();
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            }));
        }
    }
}
