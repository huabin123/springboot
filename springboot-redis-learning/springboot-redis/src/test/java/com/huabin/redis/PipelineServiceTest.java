package com.huabin.redis;

import com.huabin.redis.model.User;
import com.huabin.redis.service.PipelineAdvancedService;
import com.huabin.redis.service.PipelineService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.*;

/**
 * Pipeline 服务测试类
 * 
 * @author huabin
 * @description 测试 Pipeline 的各种功能
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class PipelineServiceTest {
    
    @Autowired
    private PipelineService pipelineService;
    
    @Autowired
    private PipelineAdvancedService pipelineAdvancedService;
    
    /**
     * 测试性能对比
     */
    @Test
    public void testPerformanceComparison() {
        System.out.println("========== 性能对比测试 ==========");
        
        // 测试不同数据量
        int[] dataSizes = {100, 1000, 5000, 10000};
        
        for (int size : dataSizes) {
            System.out.println("\n测试数据量: " + size);
            Map<String, Object> result = pipelineService.performanceComparison(size);
            System.out.println("结果: " + result);
        }
    }
    
    /**
     * 测试批量插入用户
     */
    @Test
    public void testBatchInsertUsers() {
        System.out.println("========== 批量插入用户测试 ==========");
        
        List<User> users = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            User user = new User();
            user.setId((long) i);
            user.setName("测试用户" + i);
            user.setEmail("test" + i + "@example.com");
            user.setAge(20 + (i % 50));
            users.add(user);
        }
        
        long start = System.currentTimeMillis();
        pipelineService.batchInsertUsers(users);
        long cost = System.currentTimeMillis() - start;
        
        System.out.println("插入 " + users.size() + " 个用户，耗时: " + cost + "ms");
    }
    
    /**
     * 测试批量查询用户
     */
    @Test
    public void testBatchGetUsers() {
        System.out.println("========== 批量查询用户测试 ==========");
        
        // 先插入数据
        testBatchInsertUsers();
        
        // 查询用户
        List<Long> userIds = Arrays.asList(1L, 2L, 3L, 4L, 5L, 10L, 20L, 30L);
        
        long start = System.currentTimeMillis();
        List<User> users = pipelineService.batchGetUsers(userIds);
        long cost = System.currentTimeMillis() - start;
        
        System.out.println("查询 " + userIds.size() + " 个用户，耗时: " + cost + "ms");
        System.out.println("查询结果: " + users.size() + " 个用户");
        for (User user : users) {
            System.out.println("  - " + user.getName() + " (" + user.getEmail() + ")");
        }
    }
    
    /**
     * 测试批量删除
     */
    @Test
    public void testBatchDelete() {
        System.out.println("========== 批量删除测试 ==========");
        
        List<String> keys = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            keys.add("user:" + i);
        }
        
        long start = System.currentTimeMillis();
        pipelineService.batchDeleteExpiredKeys(keys);
        long cost = System.currentTimeMillis() - start;
        
        System.out.println("删除 " + keys.size() + " 个键，耗时: " + cost + "ms");
    }
    
    /**
     * 测试批量计数器
     */
    @Test
    public void testBatchIncrement() {
        System.out.println("========== 批量计数器测试 ==========");
        
        List<Long> productIds = new ArrayList<>();
        for (long i = 1; i <= 50; i++) {
            productIds.add(i);
        }
        
        long start = System.currentTimeMillis();
        pipelineService.batchIncrementViewCount(productIds);
        long cost = System.currentTimeMillis() - start;
        
        System.out.println("增加 " + productIds.size() + " 个商品浏览量，耗时: " + cost + "ms");
    }
    
    /**
     * 测试混合操作
     */
    @Test
    public void testMixedOperations() {
        System.out.println("========== 混合操作测试 ==========");
        
        String userId = "user123";
        String sessionId = UUID.randomUUID().toString();
        
        long start = System.currentTimeMillis();
        pipelineService.mixedOperations(userId, sessionId);
        long cost = System.currentTimeMillis() - start;
        
        System.out.println("用户登录操作完成，耗时: " + cost + "ms");
        System.out.println("SessionId: " + sessionId);
    }
    
    /**
     * 测试分批执行
     */
    @Test
    public void testBatchPipelineWithLimit() {
        System.out.println("========== 分批执行测试 ==========");
        
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < 10000; i++) {
            keys.add("batch:key:" + i);
        }
        
        long start = System.currentTimeMillis();
        pipelineService.batchPipelineWithLimit(keys, 500);
        long cost = System.currentTimeMillis() - start;
        
        System.out.println("分批查询 " + keys.size() + " 个键，每批 500 个，耗时: " + cost + "ms");
    }
    
    /**
     * 测试秒杀预热
     */
    @Test
    public void testSeckillPreload() {
        System.out.println("========== 秒杀预热测试 ==========");
        
        List<PipelineAdvancedService.SeckillProduct> products = new ArrayList<>();
        for (int i = 1; i <= 1000; i++) {
            PipelineAdvancedService.SeckillProduct product = 
                new PipelineAdvancedService.SeckillProduct(
                    (long) i,
                    "秒杀商品" + i,
                    100 + (i % 500),
                    99.9 + (i % 100)
                );
            products.add(product);
        }
        
        pipelineAdvancedService.preloadSeckillStock(products);
    }
    
    /**
     * 测试批量查询秒杀库存
     */
    @Test
    public void testBatchGetSeckillStock() {
        System.out.println("========== 批量查询秒杀库存测试 ==========");
        
        // 先预热
        testSeckillPreload();
        
        // 查询库存
        List<Long> productIds = Arrays.asList(1L, 10L, 100L, 500L, 1000L);
        
        long start = System.currentTimeMillis();
        Map<Long, Integer> stockMap = pipelineAdvancedService.batchGetSeckillStock(productIds);
        long cost = System.currentTimeMillis() - start;
        
        System.out.println("查询 " + productIds.size() + " 个商品库存，耗时: " + cost + "ms");
        System.out.println("库存信息:");
        for (Map.Entry<Long, Integer> entry : stockMap.entrySet()) {
            System.out.println("  商品ID: " + entry.getKey() + ", 库存: " + entry.getValue());
        }
    }
    
    /**
     * 测试 Pipeline 不是事务
     */
    @Test
    public void testPipelineIsNotTransaction() {
        System.out.println("========== Pipeline 不是事务测试 ==========");
        
        try {
            pipelineAdvancedService.pipelineIsNotTransaction();
            System.out.println("执行完成，请检查 Redis 中的 key1 和 key2");
        } catch (Exception e) {
            System.out.println("发生异常: " + e.getMessage());
        }
    }
    
    /**
     * 测试使用事务
     */
    @Test
    public void testUseTransaction() {
        System.out.println("========== 使用事务测试 ==========");
        
        try {
            pipelineAdvancedService.useTransactionForAtomicity();
            System.out.println("事务执行成功");
        } catch (Exception e) {
            System.out.println("事务执行失败: " + e.getMessage());
        }
    }
    
    /**
     * 综合性能测试
     */
    @Test
    public void testComprehensivePerformance() {
        System.out.println("========== 综合性能测试 ==========");
        
        // 测试1: 批量写入
        System.out.println("\n1. 批量写入测试 (10000 条)");
        Map<String, String> writeData = new HashMap<>();
        for (int i = 0; i < 10000; i++) {
            writeData.put("perf:key:" + i, "value:" + i);
        }
        long writeTime = pipelineService.setWithPipeline(writeData);
        System.out.println("   写入耗时: " + writeTime + "ms");
        
        // 测试2: 批量读取
        System.out.println("\n2. 批量读取测试 (1000 条)");
        List<Long> userIds = new ArrayList<>();
        for (long i = 1; i <= 1000; i++) {
            userIds.add(i);
        }
        long readStart = System.currentTimeMillis();
        pipelineService.batchGetUsers(userIds);
        long readTime = System.currentTimeMillis() - readStart;
        System.out.println("   读取耗时: " + readTime + "ms");
        
        // 测试3: 批量计数
        System.out.println("\n3. 批量计数测试 (1000 次)");
        List<Long> productIds = new ArrayList<>();
        for (long i = 1; i <= 1000; i++) {
            productIds.add(i);
        }
        long incrStart = System.currentTimeMillis();
        pipelineService.batchIncrementViewCount(productIds);
        long incrTime = System.currentTimeMillis() - incrStart;
        System.out.println("   计数耗时: " + incrTime + "ms");
        
        // 测试4: 批量删除
        System.out.println("\n4. 批量删除测试 (10000 条)");
        List<String> deleteKeys = new ArrayList<>();
        for (int i = 0; i < 10000; i++) {
            deleteKeys.add("perf:key:" + i);
        }
        long deleteStart = System.currentTimeMillis();
        pipelineService.batchDeleteExpiredKeys(deleteKeys);
        long deleteTime = System.currentTimeMillis() - deleteStart;
        System.out.println("   删除耗时: " + deleteTime + "ms");
        
        System.out.println("\n========== 测试完成 ==========");
    }
}
