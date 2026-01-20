package com.huabin.redisson.controller;

import com.huabin.redisson.demo.BasicLockDemo;
import com.huabin.redisson.demo.ReentrantLockDemo;
import com.huabin.redisson.project.InventoryService;
import com.huabin.redisson.project.OrderService;
import com.huabin.redisson.project.SecKillService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 演示接口 Controller
 * 
 * @author huabin
 */
@RestController
@RequestMapping("/demo")
public class DemoController {
    
    @Autowired
    private BasicLockDemo basicLockDemo;
    
    @Autowired
    private ReentrantLockDemo reentrantLockDemo;
    
    @Autowired
    private SecKillService secKillService;
    
    @Autowired
    private InventoryService inventoryService;
    
    @Autowired
    private OrderService orderService;
    
    /**
     * 基础加锁演示
     */
    @GetMapping("/basic-lock")
    public String basicLock() {
        basicLockDemo.basicLockExample();
        return "基础加锁演示完成";
    }
    
    /**
     * tryLock 演示
     */
    @GetMapping("/try-lock")
    public String tryLock() {
        basicLockDemo.tryLockExample();
        return "tryLock 演示完成";
    }
    
    /**
     * WatchDog 演示
     */
    @GetMapping("/watch-dog")
    public String watchDog() {
        basicLockDemo.lockWithWatchDog();
        return "WatchDog 演示完成";
    }
    
    /**
     * 可重入锁演示
     */
    @GetMapping("/reentrant")
    public String reentrant() {
        reentrantLockDemo.reentrantExample();
        return "可重入锁演示完成";
    }
    
    /**
     * 方法嵌套调用演示
     */
    @GetMapping("/nested-method")
    public String nestedMethod() {
        reentrantLockDemo.nestedMethodExample();
        return "方法嵌套调用演示完成";
    }
    
    /**
     * 秒杀演示 - 初始化库存
     */
    @PostMapping("/seckill/init")
    public Map<String, Object> initSecKill(@RequestParam Long productId, 
                                           @RequestParam int stock) {
        secKillService.initStock(productId, stock);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "库存初始化成功");
        result.put("productId", productId);
        result.put("stock", stock);
        return result;
    }
    
    /**
     * 秒杀演示 - 单次购买
     */
    @PostMapping("/seckill/buy")
    public Map<String, Object> secKill(@RequestParam Long productId, 
                                       @RequestParam Long userId) {
        boolean success = secKillService.secKillWithLock(productId, userId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("message", success ? "秒杀成功" : "秒杀失败");
        result.put("remainStock", secKillService.getStock(productId));
        return result;
    }
    
    /**
     * 秒杀演示 - 并发测试
     */
    @PostMapping("/seckill/concurrent-test")
    public Map<String, Object> concurrentSecKill(@RequestParam Long productId, 
                                                  @RequestParam(defaultValue = "100") int threadCount) {
        // 初始化库存
        secKillService.initStock(productId, 10);
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        long startTime = System.currentTimeMillis();
        int[] successCount = {0};
        
        for (int i = 0; i < threadCount; i++) {
            final long userId = i + 1;
            executor.submit(() -> {
                try {
                    boolean success = secKillService.secKillWithLock(productId, userId);
                    if (success) {
                        synchronized (successCount) {
                            successCount[0]++;
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        long endTime = System.currentTimeMillis();
        executor.shutdown();
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("threadCount", threadCount);
        result.put("successCount", successCount[0]);
        result.put("failCount", threadCount - successCount[0]);
        result.put("remainStock", secKillService.getStock(productId));
        result.put("costTime", (endTime - startTime) + "ms");
        return result;
    }
    
    /**
     * 库存扣减演示
     */
    @PostMapping("/inventory/deduct")
    public Map<String, Object> deductStock(@RequestParam Long productId, 
                                           @RequestParam int quantity) {
        boolean success = inventoryService.deductStock(productId, quantity);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("message", success ? "库存扣减成功" : "库存扣减失败");
        result.put("remainStock", inventoryService.getCurrentStock(productId));
        return result;
    }
    
    /**
     * 批量库存扣减演示
     */
    @PostMapping("/inventory/batch-deduct")
    public Map<String, Object> batchDeductStock(@RequestParam String productIds, 
                                                @RequestParam String quantities) {
        String[] productIdArray = productIds.split(",");
        String[] quantityArray = quantities.split(",");
        
        Long[] productIdLongs = new Long[productIdArray.length];
        int[] quantityInts = new int[quantityArray.length];
        
        for (int i = 0; i < productIdArray.length; i++) {
            productIdLongs[i] = Long.parseLong(productIdArray[i].trim());
            quantityInts[i] = Integer.parseInt(quantityArray[i].trim());
        }
        
        boolean success = inventoryService.batchDeductStock(productIdLongs, quantityInts);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("message", success ? "批量库存扣减成功" : "批量库存扣减失败");
        return result;
    }
    
    /**
     * 创建订单演示
     */
    @PostMapping("/order/create")
    public Map<String, Object> createOrder(@RequestParam Long userId, 
                                           @RequestParam Long productId, 
                                           @RequestParam int quantity) {
        try {
            // 先初始化库存
            inventoryService.initStock(productId, 100);
            
            String orderNo = orderService.createOrder(userId, productId, quantity);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "订单创建成功");
            result.put("orderNo", orderNo);
            result.put("remainStock", inventoryService.getCurrentStock(productId));
            return result;
        } catch (Exception e) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", e.getMessage());
            return result;
        }
    }
    
    /**
     * 查询库存
     */
    @GetMapping("/inventory/query")
    public Map<String, Object> queryStock(@RequestParam Long productId) {
        int stock = inventoryService.getCurrentStock(productId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("productId", productId);
        result.put("stock", stock);
        return result;
    }
    
    /**
     * 初始化库存
     */
    @PostMapping("/inventory/init")
    public Map<String, Object> initStock(@RequestParam Long productId, 
                                         @RequestParam int stock) {
        inventoryService.initStock(productId, stock);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "库存初始化成功");
        result.put("productId", productId);
        result.put("stock", stock);
        return result;
    }
}
