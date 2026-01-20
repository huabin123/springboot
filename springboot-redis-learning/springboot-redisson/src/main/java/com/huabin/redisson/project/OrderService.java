package com.huabin.redisson.project;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 订单服务 - 实际项目演示
 * 
 * 场景：订单创建，防止重复下单
 * 
 * @author huabin
 */
@Service
public class OrderService {
    
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    
    @Autowired
    private RedissonClient redissonClient;
    
    @Autowired
    private InventoryService inventoryService;
    
    /**
     * 创建订单（防止重复下单）
     * 
     * @param userId 用户ID
     * @param productId 商品ID
     * @param quantity 购买数量
     * @return 订单号
     */
    public String createOrder(Long userId, Long productId, int quantity) {
        // 使用用户级别的锁，防止同一用户并发下单
        String lockKey = "order:create:lock:" + userId;
        RLock lock = redissonClient.getLock(lockKey);
        
        try {
            // 尝试获取锁：等待3秒，锁10秒后自动释放
            boolean locked = lock.tryLock(3, 10, TimeUnit.SECONDS);
            
            if (!locked) {
                log.warn("获取订单创建锁失败, userId={}", userId);
                throw new RuntimeException("系统繁忙，请稍后再试");
            }
            
            try {
                // 1. 检查用户是否有未支付订单
                if (hasUnpaidOrder(userId, productId)) {
                    log.warn("用户有未支付订单, userId={}, productId={}", userId, productId);
                    throw new RuntimeException("您有未支付的订单，请先完成支付");
                }
                
                // 2. 扣减库存
                boolean stockDeducted = inventoryService.deductStock(productId, quantity);
                if (!stockDeducted) {
                    log.warn("库存扣减失败, userId={}, productId={}, quantity={}", 
                            userId, productId, quantity);
                    throw new RuntimeException("库存不足");
                }
                
                // 3. 生成订单号
                String orderNo = generateOrderNo();
                
                // 4. 创建订单（实际项目中应该保存到数据库）
                saveOrder(orderNo, userId, productId, quantity);
                
                log.info("订单创建成功, orderNo={}, userId={}, productId={}, quantity={}", 
                        orderNo, userId, productId, quantity);
                
                return orderNo;
                
            } catch (Exception e) {
                // 发生异常时，回滚库存
                inventoryService.rollbackStock(productId, quantity);
                throw e;
            } finally {
                lock.unlock();
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("订单创建被中断, userId={}, productId={}", userId, productId, e);
            throw new RuntimeException("订单创建失败");
        }
    }
    
    /**
     * 取消订单
     * 
     * @param orderNo 订单号
     * @param userId 用户ID
     */
    public void cancelOrder(String orderNo, Long userId) {
        String lockKey = "order:cancel:lock:" + orderNo;
        RLock lock = redissonClient.getLock(lockKey);
        
        try {
            boolean locked = lock.tryLock(3, 10, TimeUnit.SECONDS);
            
            if (!locked) {
                log.warn("获取订单取消锁失败, orderNo={}", orderNo);
                throw new RuntimeException("系统繁忙，请稍后再试");
            }
            
            try {
                // 1. 查询订单信息（实际项目中从数据库查询）
                OrderInfo orderInfo = getOrderInfo(orderNo);
                
                if (orderInfo == null) {
                    log.warn("订单不存在, orderNo={}", orderNo);
                    throw new RuntimeException("订单不存在");
                }
                
                if (!orderInfo.getUserId().equals(userId)) {
                    log.warn("订单不属于当前用户, orderNo={}, userId={}", orderNo, userId);
                    throw new RuntimeException("订单不属于当前用户");
                }
                
                if (!"UNPAID".equals(orderInfo.getStatus())) {
                    log.warn("订单状态不允许取消, orderNo={}, status={}", orderNo, orderInfo.getStatus());
                    throw new RuntimeException("订单状态不允许取消");
                }
                
                // 2. 回滚库存
                boolean stockRollback = inventoryService.rollbackStock(
                        orderInfo.getProductId(), orderInfo.getQuantity());
                
                if (!stockRollback) {
                    log.error("库存回滚失败, orderNo={}", orderNo);
                    throw new RuntimeException("库存回滚失败");
                }
                
                // 3. 更新订单状态
                updateOrderStatus(orderNo, "CANCELLED");
                
                log.info("订单取消成功, orderNo={}, userId={}", orderNo, userId);
                
            } finally {
                lock.unlock();
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("订单取消被中断, orderNo={}, userId={}", orderNo, userId, e);
            throw new RuntimeException("订单取消失败");
        }
    }
    
    /**
     * 支付订单
     * 
     * @param orderNo 订单号
     * @param userId 用户ID
     */
    public void payOrder(String orderNo, Long userId) {
        String lockKey = "order:pay:lock:" + orderNo;
        RLock lock = redissonClient.getLock(lockKey);
        
        try {
            boolean locked = lock.tryLock(3, 30, TimeUnit.SECONDS);
            
            if (!locked) {
                log.warn("获取订单支付锁失败, orderNo={}", orderNo);
                throw new RuntimeException("系统繁忙，请稍后再试");
            }
            
            try {
                // 1. 查询订单信息
                OrderInfo orderInfo = getOrderInfo(orderNo);
                
                if (orderInfo == null) {
                    log.warn("订单不存在, orderNo={}", orderNo);
                    throw new RuntimeException("订单不存在");
                }
                
                if (!orderInfo.getUserId().equals(userId)) {
                    log.warn("订单不属于当前用户, orderNo={}, userId={}", orderNo, userId);
                    throw new RuntimeException("订单不属于当前用户");
                }
                
                if (!"UNPAID".equals(orderInfo.getStatus())) {
                    log.warn("订单状态不允许支付, orderNo={}, status={}", orderNo, orderInfo.getStatus());
                    throw new RuntimeException("订单状态不允许支付");
                }
                
                // 2. 调用支付接口（模拟）
                boolean paySuccess = callPaymentGateway(orderNo, orderInfo.getAmount());
                
                if (!paySuccess) {
                    log.error("支付失败, orderNo={}", orderNo);
                    throw new RuntimeException("支付失败");
                }
                
                // 3. 更新订单状态
                updateOrderStatus(orderNo, "PAID");
                
                log.info("订单支付成功, orderNo={}, userId={}", orderNo, userId);
                
            } finally {
                lock.unlock();
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("订单支付被中断, orderNo={}, userId={}", orderNo, userId, e);
            throw new RuntimeException("订单支付失败");
        }
    }
    
    /**
     * 生成订单号
     */
    private String generateOrderNo() {
        // 实际项目中应该使用分布式ID生成器（如雪花算法）
        return "ORDER_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
    }
    
    /**
     * 检查用户是否有未支付订单
     */
    private boolean hasUnpaidOrder(Long userId, Long productId) {
        // 实际项目中应该查询数据库
        return false;
    }
    
    /**
     * 保存订单
     */
    private void saveOrder(String orderNo, Long userId, Long productId, int quantity) {
        // 实际项目中应该保存到数据库
        log.debug("保存订单, orderNo={}, userId={}, productId={}, quantity={}", 
                orderNo, userId, productId, quantity);
    }
    
    /**
     * 查询订单信息
     */
    private OrderInfo getOrderInfo(String orderNo) {
        // 实际项目中应该从数据库查询
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setOrderNo(orderNo);
        orderInfo.setUserId(1L);
        orderInfo.setProductId(1001L);
        orderInfo.setQuantity(1);
        orderInfo.setAmount(99.99);
        orderInfo.setStatus("UNPAID");
        return orderInfo;
    }
    
    /**
     * 更新订单状态
     */
    private void updateOrderStatus(String orderNo, String status) {
        // 实际项目中应该更新数据库
        log.debug("更新订单状态, orderNo={}, status={}", orderNo, status);
    }
    
    /**
     * 调用支付网关
     */
    private boolean callPaymentGateway(String orderNo, double amount) {
        // 实际项目中应该调用第三方支付接口
        try {
            Thread.sleep(1000); // 模拟支付耗时
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return true;
    }
    
    /**
     * 订单信息
     */
    private static class OrderInfo {
        private String orderNo;
        private Long userId;
        private Long productId;
        private int quantity;
        private double amount;
        private String status;
        
        // Getters and Setters
        public String getOrderNo() {
            return orderNo;
        }
        
        public void setOrderNo(String orderNo) {
            this.orderNo = orderNo;
        }
        
        public Long getUserId() {
            return userId;
        }
        
        public void setUserId(Long userId) {
            this.userId = userId;
        }
        
        public Long getProductId() {
            return productId;
        }
        
        public void setProductId(Long productId) {
            this.productId = productId;
        }
        
        public int getQuantity() {
            return quantity;
        }
        
        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }
        
        public double getAmount() {
            return amount;
        }
        
        public void setAmount(double amount) {
            this.amount = amount;
        }
        
        public String getStatus() {
            return status;
        }
        
        public void setStatus(String status) {
            this.status = status;
        }
    }
}
