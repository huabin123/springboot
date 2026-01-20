package com.huabin.transaction.forupdate.service;

import com.huabin.transaction.forupdate.entity.Product;
import com.huabin.transaction.forupdate.mapper.ProductMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * FOR UPDATE在不同事务隔离级别下的行为演示Service
 * 
 * 核心问题：
 * 1. 当一个事务执行FOR UPDATE锁定记录时，其他线程能否读取这条记录？
 * 2. 读取到的是什么数据？
 * 3. 不同的事务隔离级别表现有什么不同？
 * 
 * @author huabin
 */
@Slf4j
@Service
public class ForUpdateIsolationService {

    @Autowired
    private ProductMapper productMapper;

    // ==================== READ UNCOMMITTED（读未提交）演示 ====================

    /**
     * 场景1：READ UNCOMMITTED - 事务A持有FOR UPDATE锁并修改数据（未提交）
     */
    @Transactional(isolation = Isolation.READ_UNCOMMITTED, rollbackFor = Exception.class)
    public void readUncommittedTransactionA(Long productId) {
        log.info("========== READ UNCOMMITTED - 事务A开始 ==========");
        
        // 1. 使用FOR UPDATE锁定商品
        Product product = productMapper.selectByIdForUpdate(productId);
        log.info("事务A - 锁定商品：id={}, price={}", product.getId(), product.getPrice());
        
        // 2. 修改价格（未提交）
        BigDecimal newPrice = product.getPrice().add(new BigDecimal("100"));
        productMapper.updatePrice(productId, newPrice);
        log.info("事务A - 修改价格为：{} (未提交)", newPrice);
        
        // 3. 休眠5秒，模拟业务处理
        log.info("事务A - 休眠5秒，此时事务B可以尝试读取...");
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        log.info("事务A - 即将提交事务");
    }

    /**
     * 场景1：READ UNCOMMITTED - 事务B使用快照读（普通SELECT）
     */
    @Transactional(isolation = Isolation.READ_UNCOMMITTED, rollbackFor = Exception.class)
    public void readUncommittedTransactionBSnapshotRead(Long productId) {
        log.info("========== READ UNCOMMITTED - 事务B（快照读）开始 ==========");
        
        // 普通SELECT，不加锁
        Product product = productMapper.selectById(productId);
        
        log.info("事务B - 快照读结果：id={}, price={}", product.getId(), product.getPrice());
        log.info("事务B - ✅ 能读取，⚠️ 读到事务A未提交的数据（脏读）");
    }

    /**
     * 场景1：READ UNCOMMITTED - 事务B使用当前读（FOR UPDATE）
     */
    @Transactional(isolation = Isolation.READ_UNCOMMITTED, rollbackFor = Exception.class)
    public void readUncommittedTransactionBCurrentRead(Long productId) {
        log.info("========== READ UNCOMMITTED - 事务B（当前读）开始 ==========");
        
        log.info("事务B - 尝试使用FOR UPDATE读取...");
        long startTime = System.currentTimeMillis();
        
        // FOR UPDATE，需要获取排他锁
        Product product = productMapper.selectByIdForUpdate(productId);
        
        long endTime = System.currentTimeMillis();
        log.info("事务B - 当前读结果：id={}, price={}, 等待时间：{}ms", 
                product.getId(), product.getPrice(), (endTime - startTime));
        log.info("事务B - ❌ 阻塞等待，等事务A提交后才能读取");
    }

    // ==================== READ COMMITTED（读已提交）演示 ====================

    /**
     * 场景2：READ COMMITTED - 事务A持有FOR UPDATE锁并修改数据
     */
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public void readCommittedTransactionA(Long productId) {
        log.info("========== READ COMMITTED - 事务A开始 ==========");
        
        // 1. 使用FOR UPDATE锁定商品
        Product product = productMapper.selectByIdForUpdate(productId);
        log.info("事务A - 锁定商品：id={}, price={}", product.getId(), product.getPrice());
        
        // 2. 修改价格（未提交）
        BigDecimal newPrice = product.getPrice().add(new BigDecimal("200"));
        productMapper.updatePrice(productId, newPrice);
        log.info("事务A - 修改价格为：{} (未提交)", newPrice);
        
        // 3. 休眠5秒
        log.info("事务A - 休眠5秒，此时事务B可以尝试读取...");
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        log.info("事务A - 即将提交事务");
    }

    /**
     * 场景2：READ COMMITTED - 事务B使用快照读
     */
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public void readCommittedTransactionBSnapshotRead(Long productId) {
        log.info("========== READ COMMITTED - 事务B（快照读）开始 ==========");
        
        // 第一次读取
        Product product1 = productMapper.selectById(productId);
        log.info("事务B - 第一次读取：id={}, price={}", product1.getId(), product1.getPrice());
        log.info("事务B - ✅ 能读取，✅ 读到已提交的数据（避免脏读）");
        
        // 休眠3秒（此时事务A可能已提交）
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        // 第二次读取
        Product product2 = productMapper.selectById(productId);
        log.info("事务B - 第二次读取：id={}, price={}", product2.getId(), product2.getPrice());
        
        if (!product1.getPrice().equals(product2.getPrice())) {
            log.info("事务B - ⚠️ 不可重复读：两次读取结果不同");
        }
    }

    /**
     * 场景2：READ COMMITTED - 事务B使用当前读
     */
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public void readCommittedTransactionBCurrentRead(Long productId) {
        log.info("========== READ COMMITTED - 事务B（当前读）开始 ==========");
        
        log.info("事务B - 尝试使用FOR UPDATE读取...");
        long startTime = System.currentTimeMillis();
        
        Product product = productMapper.selectByIdForUpdate(productId);
        
        long endTime = System.currentTimeMillis();
        log.info("事务B - 当前读结果：id={}, price={}, 等待时间：{}ms", 
                product.getId(), product.getPrice(), (endTime - startTime));
        log.info("事务B - ❌ 阻塞等待，等事务A提交后读到最新数据");
    }

    // ==================== REPEATABLE READ（可重复读，MySQL默认）演示 ====================

    /**
     * 场景3：REPEATABLE READ - 事务A持有FOR UPDATE锁并修改数据
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ, rollbackFor = Exception.class)
    public void repeatableReadTransactionA(Long productId) {
        log.info("========== REPEATABLE READ - 事务A开始 ==========");
        
        // 1. 使用FOR UPDATE锁定商品
        Product product = productMapper.selectByIdForUpdate(productId);
        log.info("事务A - 锁定商品：id={}, price={}", product.getId(), product.getPrice());
        
        // 2. 修改价格
        BigDecimal newPrice = product.getPrice().add(new BigDecimal("300"));
        productMapper.updatePrice(productId, newPrice);
        log.info("事务A - 修改价格为：{}", newPrice);
        
        // 3. 提交事务
        log.info("事务A - 提交事务");
    }

    /**
     * 场景3：REPEATABLE READ - 事务B使用快照读（演示MVCC）
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ, rollbackFor = Exception.class)
    public void repeatableReadTransactionBSnapshotRead(Long productId) {
        log.info("========== REPEATABLE READ - 事务B（快照读）开始 ==========");
        
        // 第一次读取（建立快照）
        Product product1 = productMapper.selectById(productId);
        log.info("事务B - 第一次快照读：id={}, price={}", product1.getId(), product1.getPrice());
        
        // 休眠3秒（等待事务A提交）
        log.info("事务B - 休眠3秒，等待事务A提交...");
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        // 第二次读取（仍然读快照）
        Product product2 = productMapper.selectById(productId);
        log.info("事务B - 第二次快照读：id={}, price={}", product2.getId(), product2.getPrice());
        log.info("事务B - ✅ 可重复读：两次读取结果一致（MVCC快照）");
        
        // 第三次读取（当前读）
        Product product3 = productMapper.selectByIdForUpdate(productId);
        log.info("事务B - 第三次当前读：id={}, price={}", product3.getId(), product3.getPrice());
        log.info("事务B - ⚠️ 当前读：读到事务A提交后的最新数据");
    }

    /**
     * 场景3：REPEATABLE READ - 事务B使用当前读
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ, rollbackFor = Exception.class)
    public void repeatableReadTransactionBCurrentRead(Long productId) {
        log.info("========== REPEATABLE READ - 事务B（当前读）开始 ==========");
        
        log.info("事务B - 尝试使用FOR UPDATE读取...");
        long startTime = System.currentTimeMillis();
        
        Product product = productMapper.selectByIdForUpdate(productId);
        
        long endTime = System.currentTimeMillis();
        log.info("事务B - 当前读结果：id={}, price={}, 等待时间：{}ms", 
                product.getId(), product.getPrice(), (endTime - startTime));
        log.info("事务B - ❌ 阻塞等待，等事务A提交后读到最新数据");
    }

    // ==================== SERIALIZABLE（串行化）演示 ====================

    /**
     * 场景4：SERIALIZABLE - 事务A持有FOR UPDATE锁
     */
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public void serializableTransactionA(Long productId) {
        log.info("========== SERIALIZABLE - 事务A开始 ==========");
        
        // 使用FOR UPDATE锁定商品
        Product product = productMapper.selectByIdForUpdate(productId);
        log.info("事务A - 锁定商品：id={}, price={}", product.getId(), product.getPrice());
        
        // 休眠5秒
        log.info("事务A - 休眠5秒...");
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        log.info("事务A - 即将提交事务");
    }

    /**
     * 场景4：SERIALIZABLE - 事务B使用普通SELECT（自动加共享锁）
     */
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public void serializableTransactionBSnapshotRead(Long productId) {
        log.info("========== SERIALIZABLE - 事务B（普通SELECT）开始 ==========");
        
        log.info("事务B - 尝试使用普通SELECT读取...");
        long startTime = System.currentTimeMillis();
        
        // 普通SELECT在SERIALIZABLE级别下会自动加共享锁
        Product product = productMapper.selectById(productId);
        
        long endTime = System.currentTimeMillis();
        log.info("事务B - 读取结果：id={}, price={}, 等待时间：{}ms", 
                product.getId(), product.getPrice(), (endTime - startTime));
        log.info("事务B - ❌ 阻塞等待：SERIALIZABLE级别下，普通SELECT也会加共享锁");
        log.info("事务B - 共享锁与排他锁冲突，必须等待事务A释放锁");
    }

    // ==================== 实战场景演示 ====================

    /**
     * 实战场景1：秒杀扣减库存（正确做法）
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ, rollbackFor = Exception.class)
    public void seckillCorrect(Long productId, Integer quantity) {
        log.info("========== 秒杀扣减库存（正确做法）==========");
        
        // 1. 使用FOR UPDATE锁定商品（当前读）
        Product product = productMapper.selectByIdForUpdate(productId);
        log.info("锁定商品：id={}, stock={}", product.getId(), product.getStock());
        
        // 2. 检查库存
        if (product.getStock() < quantity) {
            log.error("库存不足：当前库存={}, 需要={}", product.getStock(), quantity);
            throw new RuntimeException("库存不足");
        }
        
        // 3. 扣减库存
        int rows = productMapper.deductStock(productId, quantity);
        if (rows == 0) {
            log.error("扣减库存失败");
            throw new RuntimeException("扣减库存失败");
        }
        
        log.info("扣减库存成功：扣减数量={}, 剩余库存={}", quantity, product.getStock() - quantity);
        log.info("✅ FOR UPDATE保证了同一时间只有一个事务能扣减库存");
    }

    /**
     * 实战场景2：秒杀扣减库存（错误做法 - 快照读）
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ, rollbackFor = Exception.class)
    public void seckillWrong(Long productId, Integer quantity) {
        log.info("========== 秒杀扣减库存（错误做法）==========");
        
        // 1. 使用普通SELECT查询库存（快照读）
        Product product = productMapper.selectById(productId);
        log.info("查询商品：id={}, stock={}", product.getId(), product.getStock());
        
        // 2. 检查库存（基于快照数据）
        if (product.getStock() < quantity) {
            log.error("库存不足：当前库存={}, 需要={}", product.getStock(), quantity);
            throw new RuntimeException("库存不足");
        }
        
        // 3. 扣减库存
        int rows = productMapper.deductStock(productId, quantity);
        if (rows == 0) {
            log.error("扣减库存失败");
            throw new RuntimeException("扣减库存失败");
        }
        
        log.info("扣减库存成功：扣减数量={}", quantity);
        log.warn("⚠️ 问题：多个事务可能同时读到相同的库存，导致超卖");
    }

    /**
     * 实战场景3：查询商品详情（推荐做法 - 快照读）
     */
    @Transactional(isolation = Isolation.READ_COMMITTED, readOnly = true)
    public Product queryProductDetail(Long productId) {
        log.info("========== 查询商品详情（推荐做法）==========");
        
        // 使用普通SELECT，不加锁
        Product product = productMapper.selectById(productId);
        log.info("查询商品：id={}, name={}, price={}, stock={}", 
                product.getId(), product.getName(), product.getPrice(), product.getStock());
        
        log.info("✅ 优点：不阻塞其他事务，性能高，适合读多写少的场景");
        
        return product;
    }

    /**
     * 实战场景4：查询商品详情（不推荐 - FOR UPDATE）
     */
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public Product queryProductDetailWithLock(Long productId) {
        log.info("========== 查询商品详情（不推荐做法）==========");
        
        // 使用FOR UPDATE加锁
        Product product = productMapper.selectByIdForUpdate(productId);
        log.info("查询商品：id={}, name={}, price={}, stock={}", 
                product.getId(), product.getName(), product.getPrice(), product.getStock());
        
        log.warn("❌ 缺点：阻塞其他事务的读写，性能差，可能导致死锁");
        
        return product;
    }

    // ==================== 工具方法 ====================

    /**
     * 初始化测试数据
     */
    public void initTestData() {
        log.info("初始化测试数据...");
        productMapper.initTestData();
        log.info("测试数据初始化完成");
    }

    /**
     * 查询商品（不开启事务）
     */
    public Product queryProduct(Long productId) {
        return productMapper.selectById(productId);
    }
}
