package com.huabin.redis.controller;

import com.huabin.redis.problem.cache.CacheAvalancheProblem;
import com.huabin.redis.problem.cache.CacheBreakdownProblem;
import com.huabin.redis.problem.cache.CachePenetrationProblem;
import com.huabin.redis.problem.cluster.HotKeyProblem;
import com.huabin.redis.problem.memory.MemoryLeakProblem;
import com.huabin.redis.problem.performance.BigKeyProblem;
import com.huabin.redis.problem.performance.BlockingProblem;
import com.huabin.redis.scenario.SeckillScenario;
import com.huabin.redis.solution.cache.CacheAvalancheSolution;
import com.huabin.redis.solution.cache.CacheBreakdownSolution;
import com.huabin.redis.solution.cache.CachePenetrationSolution;
import com.huabin.redis.solution.cluster.HotKeySolution;
import com.huabin.redis.solution.memory.MemoryOptimization;
import com.huabin.redis.solution.performance.BigKeySolution;
import com.huabin.redis.solution.performance.BlockingSolution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Redis 问题演示控制器
 * 
 * 提供HTTP接口演示各种Redis问题和解决方案
 */
@RestController
@RequestMapping("/redis/demo")
public class RedisProblemDemoController {
    
    @Autowired
    private CachePenetrationProblem cachePenetrationProblem;
    
    @Autowired
    private CachePenetrationSolution cachePenetrationSolution;
    
    @Autowired
    private CacheBreakdownProblem cacheBreakdownProblem;
    
    @Autowired
    private CacheBreakdownSolution cacheBreakdownSolution;
    
    @Autowired
    private CacheAvalancheProblem cacheAvalancheProblem;
    
    @Autowired
    private CacheAvalancheSolution cacheAvalancheSolution;
    
    @Autowired
    private BigKeyProblem bigKeyProblem;
    
    @Autowired
    private BigKeySolution bigKeySolution;
    
    @Autowired
    private BlockingProblem blockingProblem;
    
    @Autowired
    private BlockingSolution blockingSolution;
    
    @Autowired
    private MemoryLeakProblem memoryLeakProblem;
    
    @Autowired
    private MemoryOptimization memoryOptimization;
    
    @Autowired
    private HotKeyProblem hotKeyProblem;
    
    @Autowired
    private HotKeySolution hotKeySolution;
    
    @Autowired
    private SeckillScenario seckillScenario;
    
    /**
     * 首页：问题列表
     */
    @GetMapping("/")
    public String index() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Redis 生产问题演示系统 ===\n\n");
        sb.append("【缓存问题】\n");
        sb.append("1. /redis/demo/cache/penetration/problem - 缓存穿透问题\n");
        sb.append("2. /redis/demo/cache/penetration/solution - 缓存穿透解决方案\n");
        sb.append("3. /redis/demo/cache/breakdown/problem - 缓存击穿问题\n");
        sb.append("4. /redis/demo/cache/breakdown/solution - 缓存击穿解决方案\n");
        sb.append("5. /redis/demo/cache/avalanche/problem - 缓存雪崩问题\n");
        sb.append("6. /redis/demo/cache/avalanche/solution - 缓存雪崩解决方案\n\n");
        
        sb.append("【性能问题】\n");
        sb.append("7. /redis/demo/performance/bigkey/problem - BigKey问题\n");
        sb.append("8. /redis/demo/performance/bigkey/solution - BigKey解决方案\n");
        sb.append("9. /redis/demo/performance/blocking/problem - 阻塞问题\n");
        sb.append("10. /redis/demo/performance/blocking/solution - 阻塞解决方案\n\n");
        
        sb.append("【内存问题】\n");
        sb.append("11. /redis/demo/memory/leak/problem - 内存泄漏问题\n");
        sb.append("12. /redis/demo/memory/optimization - 内存优化方案\n\n");
        
        sb.append("【集群问题】\n");
        sb.append("13. /redis/demo/cluster/hotkey/problem - 热点Key问题\n");
        sb.append("14. /redis/demo/cluster/hotkey/solution - 热点Key解决方案\n\n");
        
        sb.append("【完整场景】\n");
        sb.append("15. /redis/demo/scenario/seckill - 秒杀场景完整演示\n");
        
        return sb.toString();
    }
    
    // ========== 缓存问题 ==========
    
    @GetMapping("/cache/penetration/problem")
    public String cachePenetrationProblem() {
        cachePenetrationProblem.simulatePenetrationAttack();
        return "缓存穿透问题演示完成，请查看控制台日志";
    }
    
    @GetMapping("/cache/penetration/solution")
    public String cachePenetrationSolution() {
        cachePenetrationSolution.comparePerformance();
        return "缓存穿透解决方案演示完成，请查看控制台日志";
    }
    
    @GetMapping("/cache/breakdown/problem")
    public String cacheBreakdownProblem() throws InterruptedException {
        cacheBreakdownProblem.simulateBreakdownAttack();
        return "缓存击穿问题演示完成，请查看控制台日志";
    }
    
    @GetMapping("/cache/breakdown/solution")
    public String cacheBreakdownSolution() throws InterruptedException {
        cacheBreakdownSolution.comparePerformance();
        return "缓存击穿解决方案演示完成，请查看控制台日志";
    }
    
    @GetMapping("/cache/avalanche/problem")
    public String cacheAvalancheProblem() throws InterruptedException {
        cacheAvalancheProblem.simulateAvalanche();
        return "缓存雪崩问题演示完成，请查看控制台日志";
    }
    
    @GetMapping("/cache/avalanche/solution")
    public String cacheAvalancheSolution() throws InterruptedException {
        cacheAvalancheSolution.comparePerformance();
        return "缓存雪崩解决方案演示完成，请查看控制台日志";
    }
    
    // ========== 性能问题 ==========
    
    @GetMapping("/performance/bigkey/problem")
    public String bigKeyProblem() {
        bigKeyProblem.createBigHash_Problem();
        bigKeyProblem.createBigList_Problem();
        bigKeyProblem.createBigString_Problem();
        bigKeyProblem.demonstrateBigKeyProblems();
        return "BigKey问题演示完成，请查看控制台日志";
    }
    
    @GetMapping("/performance/bigkey/solution")
    public String bigKeySolution() {
        bigKeySolution.splitBigHash();
        bigKeySolution.splitBigList();
        bigKeySolution.optimizationSuggestions();
        return "BigKey解决方案演示完成，请查看控制台日志";
    }
    
    @GetMapping("/performance/blocking/problem")
    public String blockingProblem() {
        blockingProblem.useKeysCommand_Problem();
        blockingProblem.slowQuery_Problem();
        blockingProblem.blockingProblemsSummary();
        return "阻塞问题演示完成，请查看控制台日志";
    }
    
    @GetMapping("/performance/blocking/solution")
    public String blockingSolution() {
        blockingSolution.useScanInsteadOfKeys("test:*");
        blockingSolution.batchPipeline();
        blockingSolution.useZSetInsteadOfSort();
        blockingSolution.performanceOptimizationSummary();
        return "阻塞解决方案演示完成，请查看控制台日志";
    }
    
    // ========== 内存问题 ==========
    
    @GetMapping("/memory/leak/problem")
    public String memoryLeakProblem() {
        memoryLeakProblem.noExpireTime_Problem();
        memoryLeakProblem.unlimitedCollection_Problem();
        memoryLeakProblem.wrongEvictionPolicy_Problem();
        memoryLeakProblem.memoryLeakDangers();
        return "内存泄漏问题演示完成，请查看控制台日志";
    }
    
    @GetMapping("/memory/optimization")
    public String memoryOptimization() {
        memoryOptimization.setProperExpireTime();
        memoryOptimization.limitCollectionSize();
        memoryOptimization.configureEvictionPolicy();
        memoryOptimization.optimizationSummary();
        return "内存优化方案演示完成，请查看控制台日志";
    }
    
    // ========== 集群问题 ==========
    
    @GetMapping("/cluster/hotkey/problem")
    public String hotKeyProblem() throws InterruptedException {
        hotKeyProblem.hotKeyProblem();
        hotKeyProblem.simulateHotKeyAccess();
        hotKeyProblem.hotKeyDangers();
        return "热点Key问题演示完成，请查看控制台日志";
    }
    
    @GetMapping("/cluster/hotkey/solution")
    public String hotKeySolution() {
        hotKeySolution.hotKeyOptimizationSummary();
        return "热点Key解决方案演示完成，请查看控制台日志";
    }
    
    // ========== 完整场景 ==========
    
    @GetMapping("/scenario/seckill")
    public String seckillScenario() throws InterruptedException {
        seckillScenario.fullScenarioDemo();
        return "秒杀场景演示完成，请查看控制台日志";
    }
}
