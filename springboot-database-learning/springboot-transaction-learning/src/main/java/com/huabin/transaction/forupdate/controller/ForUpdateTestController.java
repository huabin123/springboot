package com.huabin.transaction.forupdate.controller;

import com.huabin.transaction.forupdate.entity.Product;
import com.huabin.transaction.forupdate.service.ForUpdateIsolationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * FOR UPDATE测试Controller
 * 
 * 提供HTTP接口来演示FOR UPDATE在不同事务隔离级别下的行为
 * 
 * @author huabin
 */
@Slf4j
@RestController
@RequestMapping("/forupdate")
public class ForUpdateTestController {

    @Autowired
    private ForUpdateIsolationService forUpdateIsolationService;

    // 线程池，用于模拟并发事务
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    /**
     * 初始化测试数据
     * 
     * GET http://localhost:8080/forupdate/init
     */
    @GetMapping("/init")
    public Map<String, Object> initTestData() {
        forUpdateIsolationService.initTestData();
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "测试数据初始化成功");
        return result;
    }

    /**
     * 查询商品信息
     * 
     * GET http://localhost:8080/forupdate/query?productId=1
     */
    @GetMapping("/query")
    public Map<String, Object> queryProduct(@RequestParam Long productId) {
        Product product = forUpdateIsolationService.queryProduct(productId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", product);
        return result;
    }

    // ==================== READ UNCOMMITTED 演示 ====================

    /**
     * 演示READ UNCOMMITTED - 快照读
     * 
     * 测试步骤：
     * 1. 先调用此接口（事务A持有锁）
     * 2. 在5秒内调用 /forupdate/read-uncommitted/snapshot-read?productId=1（事务B快照读）
     * 
     * GET http://localhost:8080/forupdate/read-uncommitted/demo?productId=1
     */
    @GetMapping("/read-uncommitted/demo")
    public Map<String, Object> readUncommittedDemo(@RequestParam Long productId) {
        log.info("========== 开始演示 READ UNCOMMITTED ==========");
        
        // 异步执行事务A
        CompletableFuture.runAsync(() -> {
            forUpdateIsolationService.readUncommittedTransactionA(productId);
        }, executorService);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "事务A已启动，请在5秒内调用其他接口测试");
        return result;
    }

    /**
     * 演示READ UNCOMMITTED - 事务B快照读
     * 
     * GET http://localhost:8080/forupdate/read-uncommitted/snapshot-read?productId=1
     */
    @GetMapping("/read-uncommitted/snapshot-read")
    public Map<String, Object> readUncommittedSnapshotRead(@RequestParam Long productId) {
        forUpdateIsolationService.readUncommittedTransactionBSnapshotRead(productId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "事务B快照读完成，查看日志了解详情");
        return result;
    }

    /**
     * 演示READ UNCOMMITTED - 事务B当前读
     * 
     * GET http://localhost:8080/forupdate/read-uncommitted/current-read?productId=1
     */
    @GetMapping("/read-uncommitted/current-read")
    public Map<String, Object> readUncommittedCurrentRead(@RequestParam Long productId) {
        forUpdateIsolationService.readUncommittedTransactionBCurrentRead(productId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "事务B当前读完成，查看日志了解详情");
        return result;
    }

    // ==================== READ COMMITTED 演示 ====================

    /**
     * 演示READ COMMITTED - 快照读
     * 
     * GET http://localhost:8080/forupdate/read-committed/demo?productId=1
     */
    @GetMapping("/read-committed/demo")
    public Map<String, Object> readCommittedDemo(@RequestParam Long productId) {
        log.info("========== 开始演示 READ COMMITTED ==========");
        
        // 异步执行事务A
        CompletableFuture.runAsync(() -> {
            forUpdateIsolationService.readCommittedTransactionA(productId);
        }, executorService);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "事务A已启动，请在5秒内调用其他接口测试");
        return result;
    }

    /**
     * 演示READ COMMITTED - 事务B快照读
     * 
     * GET http://localhost:8080/forupdate/read-committed/snapshot-read?productId=1
     */
    @GetMapping("/read-committed/snapshot-read")
    public Map<String, Object> readCommittedSnapshotRead(@RequestParam Long productId) {
        forUpdateIsolationService.readCommittedTransactionBSnapshotRead(productId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "事务B快照读完成，查看日志了解详情");
        return result;
    }

    /**
     * 演示READ COMMITTED - 事务B当前读
     * 
     * GET http://localhost:8080/forupdate/read-committed/current-read?productId=1
     */
    @GetMapping("/read-committed/current-read")
    public Map<String, Object> readCommittedCurrentRead(@RequestParam Long productId) {
        forUpdateIsolationService.readCommittedTransactionBCurrentRead(productId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "事务B当前读完成，查看日志了解详情");
        return result;
    }

    // ==================== REPEATABLE READ 演示 ====================

    /**
     * 演示REPEATABLE READ - 快照读（MVCC）
     * 
     * 测试步骤：
     * 1. 先调用 /forupdate/repeatable-read/snapshot-read?productId=1（事务B开始，建立快照）
     * 2. 立即调用 /forupdate/repeatable-read/demo?productId=1（事务A修改并提交）
     * 3. 观察事务B的三次读取结果
     * 
     * GET http://localhost:8080/forupdate/repeatable-read/demo?productId=1
     */
    @GetMapping("/repeatable-read/demo")
    public Map<String, Object> repeatableReadDemo(@RequestParam Long productId) {
        log.info("========== 开始演示 REPEATABLE READ ==========");
        
        forUpdateIsolationService.repeatableReadTransactionA(productId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "事务A已提交，查看日志了解详情");
        return result;
    }

    /**
     * 演示REPEATABLE READ - 事务B快照读
     * 
     * GET http://localhost:8080/forupdate/repeatable-read/snapshot-read?productId=1
     */
    @GetMapping("/repeatable-read/snapshot-read")
    public Map<String, Object> repeatableReadSnapshotRead(@RequestParam Long productId) {
        forUpdateIsolationService.repeatableReadTransactionBSnapshotRead(productId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "事务B快照读完成，查看日志了解详情");
        return result;
    }

    /**
     * 演示REPEATABLE READ - 事务B当前读
     * 
     * GET http://localhost:8080/forupdate/repeatable-read/current-read?productId=1
     */
    @GetMapping("/repeatable-read/current-read")
    public Map<String, Object> repeatableReadCurrentRead(@RequestParam Long productId) {
        forUpdateIsolationService.repeatableReadTransactionBCurrentRead(productId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "事务B当前读完成，查看日志了解详情");
        return result;
    }

    // ==================== SERIALIZABLE 演示 ====================

    /**
     * 演示SERIALIZABLE - 普通SELECT也会加锁
     * 
     * GET http://localhost:8080/forupdate/serializable/demo?productId=1
     */
    @GetMapping("/serializable/demo")
    public Map<String, Object> serializableDemo(@RequestParam Long productId) {
        log.info("========== 开始演示 SERIALIZABLE ==========");
        
        // 异步执行事务A
        CompletableFuture.runAsync(() -> {
            forUpdateIsolationService.serializableTransactionA(productId);
        }, executorService);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "事务A已启动，请在5秒内调用其他接口测试");
        return result;
    }

    /**
     * 演示SERIALIZABLE - 事务B普通SELECT
     * 
     * GET http://localhost:8080/forupdate/serializable/snapshot-read?productId=1
     */
    @GetMapping("/serializable/snapshot-read")
    public Map<String, Object> serializableSnapshotRead(@RequestParam Long productId) {
        forUpdateIsolationService.serializableTransactionBSnapshotRead(productId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "事务B普通SELECT完成，查看日志了解详情");
        return result;
    }

    // ==================== 实战场景演示 ====================

    /**
     * 实战场景：秒杀扣减库存（正确做法）
     * 
     * 测试步骤：
     * 1. 先调用 /forupdate/init 初始化数据
     * 2. 多次并发调用此接口，观察库存扣减情况
     * 
     * POST http://localhost:8080/forupdate/seckill/correct?productId=1&quantity=10
     */
    @PostMapping("/seckill/correct")
    public Map<String, Object> seckillCorrect(@RequestParam Long productId, 
                                               @RequestParam Integer quantity) {
        try {
            forUpdateIsolationService.seckillCorrect(productId, quantity);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "扣减库存成功");
            return result;
        } catch (Exception e) {
            log.error("扣减库存失败：{}", e.getMessage());
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", e.getMessage());
            return result;
        }
    }

    /**
     * 实战场景：秒杀扣减库存（错误做法）
     * 
     * POST http://localhost:8080/forupdate/seckill/wrong?productId=1&quantity=10
     */
    @PostMapping("/seckill/wrong")
    public Map<String, Object> seckillWrong(@RequestParam Long productId, 
                                             @RequestParam Integer quantity) {
        try {
            forUpdateIsolationService.seckillWrong(productId, quantity);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "扣减库存成功");
            return result;
        } catch (Exception e) {
            log.error("扣减库存失败：{}", e.getMessage());
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", e.getMessage());
            return result;
        }
    }

    /**
     * 实战场景：查询商品详情（推荐做法）
     * 
     * GET http://localhost:8080/forupdate/query-detail/recommend?productId=1
     */
    @GetMapping("/query-detail/recommend")
    public Map<String, Object> queryDetailRecommend(@RequestParam Long productId) {
        Product product = forUpdateIsolationService.queryProductDetail(productId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", product);
        result.put("message", "使用快照读，不加锁，性能高");
        return result;
    }

    /**
     * 实战场景：查询商品详情（不推荐做法）
     * 
     * GET http://localhost:8080/forupdate/query-detail/not-recommend?productId=1
     */
    @GetMapping("/query-detail/not-recommend")
    public Map<String, Object> queryDetailNotRecommend(@RequestParam Long productId) {
        Product product = forUpdateIsolationService.queryProductDetailWithLock(productId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", product);
        result.put("message", "使用FOR UPDATE，加锁，性能差");
        return result;
    }

    /**
     * 并发测试：模拟多个线程同时秒杀
     * 
     * 测试步骤：
     * 1. 先调用 /forupdate/init 初始化数据（商品1库存100）
     * 2. 调用此接口，模拟10个线程同时抢购
     * 3. 观察日志和最终库存
     * 
     * GET http://localhost:8080/forupdate/concurrent-test?productId=1&threadCount=10&quantity=10&useCorrect=true
     */
    @GetMapping("/concurrent-test")
    public Map<String, Object> concurrentTest(@RequestParam Long productId,
                                               @RequestParam(defaultValue = "10") Integer threadCount,
                                               @RequestParam(defaultValue = "10") Integer quantity,
                                               @RequestParam(defaultValue = "true") Boolean useCorrect) {
        log.info("========== 并发测试开始 ==========");
        log.info("商品ID：{}, 线程数：{}, 每次扣减：{}, 使用正确方法：{}", 
                productId, threadCount, quantity, useCorrect);
        
        // 查询初始库存
        Product beforeProduct = forUpdateIsolationService.queryProduct(productId);
        log.info("初始库存：{}", beforeProduct.getStock());
        
        // 并发执行
        CompletableFuture<?>[] futures = new CompletableFuture[threadCount];
        for (int i = 0; i < threadCount; i++) {
            final int threadNum = i + 1;
            futures[i] = CompletableFuture.runAsync(() -> {
                try {
                    log.info("线程{} 开始抢购", threadNum);
                    if (useCorrect) {
                        forUpdateIsolationService.seckillCorrect(productId, quantity);
                    } else {
                        forUpdateIsolationService.seckillWrong(productId, quantity);
                    }
                    log.info("线程{} 抢购成功", threadNum);
                } catch (Exception e) {
                    log.error("线程{} 抢购失败：{}", threadNum, e.getMessage());
                }
            }, executorService);
        }
        
        // 等待所有线程完成
        CompletableFuture.allOf(futures).join();
        
        // 查询最终库存
        Product afterProduct = forUpdateIsolationService.queryProduct(productId);
        log.info("最终库存：{}", afterProduct.getStock());
        
        int expectedStock = beforeProduct.getStock() - (threadCount * quantity);
        boolean isCorrect = afterProduct.getStock() >= 0 && 
                           (afterProduct.getStock() == expectedStock || afterProduct.getStock() > expectedStock);
        
        log.info("========== 并发测试结束 ==========");
        log.info("预期库存：{}, 实际库存：{}, 结果：{}", 
                expectedStock, afterProduct.getStock(), isCorrect ? "正确" : "错误（超卖）");
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("beforeStock", beforeProduct.getStock());
        result.put("afterStock", afterProduct.getStock());
        result.put("expectedStock", expectedStock);
        result.put("isCorrect", isCorrect);
        result.put("message", isCorrect ? "库存扣减正确" : "发生超卖");
        return result;
    }
}
