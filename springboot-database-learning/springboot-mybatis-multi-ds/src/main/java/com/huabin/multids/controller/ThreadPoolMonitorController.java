package com.huabin.multids.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @Author huabin
 * @DateTime 2025-12-29
 * @Desc 线程池监控控制器
 * 
 * 提供线程池运行状态的监控接口
 */
@RestController
@RequestMapping("/api/monitor")
public class ThreadPoolMonitorController {

    @Autowired
    @Qualifier("productCreateExecutor")
    private ThreadPoolExecutor productCreateExecutor;

    @Autowired
    @Qualifier("asyncExecutor")
    private ThreadPoolExecutor asyncExecutor;

    /**
     * 查询产品创建线程池状态
     * 
     * 测试命令：
     * curl http://localhost:8080/api/monitor/product-thread-pool
     */
    @GetMapping("/product-thread-pool")
    public Map<String, Object> getProductThreadPoolStatus() {
        return buildThreadPoolStatus("产品创建线程池", productCreateExecutor);
    }

    /**
     * 查询通用异步线程池状态
     * 
     * 测试命令：
     * curl http://localhost:8080/api/monitor/async-thread-pool
     */
    @GetMapping("/async-thread-pool")
    public Map<String, Object> getAsyncThreadPoolStatus() {
        return buildThreadPoolStatus("通用异步线程池", asyncExecutor);
    }

    /**
     * 查询所有线程池状态
     * 
     * 测试命令：
     * curl http://localhost:8080/api/monitor/all-thread-pools
     */
    @GetMapping("/all-thread-pools")
    public Map<String, Object> getAllThreadPoolsStatus() {
        Map<String, Object> result = new HashMap<>();
        result.put("productCreateExecutor", buildThreadPoolStatus("产品创建线程池", productCreateExecutor));
        result.put("asyncExecutor", buildThreadPoolStatus("通用异步线程池", asyncExecutor));
        return result;
    }

    /**
     * 构建线程池状态信息
     */
    private Map<String, Object> buildThreadPoolStatus(String poolName, ThreadPoolExecutor executor) {
        Map<String, Object> status = new HashMap<>();
        
        // 基本信息
        status.put("poolName", poolName);
        status.put("isShutdown", executor.isShutdown());
        status.put("isTerminated", executor.isTerminated());
        status.put("isTerminating", executor.isTerminating());
        
        // 线程池配置
        Map<String, Object> config = new HashMap<>();
        config.put("corePoolSize", executor.getCorePoolSize());
        config.put("maximumPoolSize", executor.getMaximumPoolSize());
        config.put("keepAliveTime", executor.getKeepAliveTime(java.util.concurrent.TimeUnit.SECONDS) + "秒");
        status.put("config", config);
        
        // 当前状态
        Map<String, Object> currentStatus = new HashMap<>();
        currentStatus.put("poolSize", executor.getPoolSize());
        currentStatus.put("activeCount", executor.getActiveCount());
        currentStatus.put("largestPoolSize", executor.getLargestPoolSize());
        status.put("currentStatus", currentStatus);
        
        // 任务统计
        Map<String, Object> taskStats = new HashMap<>();
        taskStats.put("taskCount", executor.getTaskCount());
        taskStats.put("completedTaskCount", executor.getCompletedTaskCount());
        taskStats.put("queueSize", executor.getQueue().size());
        taskStats.put("queueRemainingCapacity", executor.getQueue().remainingCapacity());
        status.put("taskStats", taskStats);
        
        // 计算使用率
        Map<String, Object> usage = new HashMap<>();
        int corePoolSize = executor.getCorePoolSize();
        int activeCount = executor.getActiveCount();
        int queueSize = executor.getQueue().size();
        int queueCapacity = queueSize + executor.getQueue().remainingCapacity();
        
        double threadUsageRate = corePoolSize > 0 ? (double) activeCount / corePoolSize * 100 : 0;
        double queueUsageRate = queueCapacity > 0 ? (double) queueSize / queueCapacity * 100 : 0;
        
        usage.put("threadUsageRate", String.format("%.2f%%", threadUsageRate));
        usage.put("queueUsageRate", String.format("%.2f%%", queueUsageRate));
        status.put("usage", usage);
        
        // 健康状态评估
        String healthStatus = evaluateHealthStatus(executor);
        status.put("healthStatus", healthStatus);
        
        return status;
    }

    /**
     * 评估线程池健康状态
     */
    private String evaluateHealthStatus(ThreadPoolExecutor executor) {
        int activeCount = executor.getActiveCount();
        int maximumPoolSize = executor.getMaximumPoolSize();
        int queueSize = executor.getQueue().size();
        int queueCapacity = queueSize + executor.getQueue().remainingCapacity();
        
        // 计算使用率
        double threadUsageRate = maximumPoolSize > 0 ? (double) activeCount / maximumPoolSize * 100 : 0;
        double queueUsageRate = queueCapacity > 0 ? (double) queueSize / queueCapacity * 100 : 0;
        
        if (executor.isShutdown()) {
            return "已关闭";
        } else if (threadUsageRate >= 90 || queueUsageRate >= 90) {
            return "警告：资源使用率过高";
        } else if (threadUsageRate >= 70 || queueUsageRate >= 70) {
            return "注意：资源使用率较高";
        } else {
            return "正常";
        }
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "线程池监控服务正常");
        result.put("productThreadPoolHealth", evaluateHealthStatus(productCreateExecutor));
        result.put("asyncThreadPoolHealth", evaluateHealthStatus(asyncExecutor));
        return result;
    }
}
