package com.huabin.transaction.service;

import com.huabin.transaction.entity.Orders;
import com.huabin.transaction.entity.TransactionLog;
import com.huabin.transaction.mapper.OrdersMapper;
import com.huabin.transaction.mapper.TransactionLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Spring事务传播机制演示Service
 * 
 * 7种事务传播行为：
 * 
 * 1. REQUIRED（默认）：如果当前存在事务，则加入该事务；如果不存在，则创建新事务
 * 2. SUPPORTS：如果当前存在事务，则加入该事务；如果不存在，则以非事务方式执行
 * 3. MANDATORY：如果当前存在事务，则加入该事务；如果不存在，则抛出异常
 * 4. REQUIRES_NEW：创建新事务，如果当前存在事务，则挂起当前事务
 * 5. NOT_SUPPORTED：以非事务方式执行，如果当前存在事务，则挂起当前事务
 * 6. NEVER：以非事务方式执行，如果当前存在事务，则抛出异常
 * 7. NESTED：如果当前存在事务，则在嵌套事务内执行；如果不存在，则创建新事务
 * 
 * @author huabin
 */
@Slf4j
@Service
public class TransactionPropagationService {

    @Autowired
    private OrdersMapper ordersMapper;
    
    @Autowired
    private TransactionLogMapper logMapper;
    
    @Autowired
    private TransactionPropagationService self; // 用于解决自调用问题

    /**
     * REQUIRED（默认）：加入当前事务，如果不存在则创建新事务
     */
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void requiredDemo() {
        log.info("=== REQUIRED 传播行为演示 ===");
        
        Orders order = new Orders();
        order.setOrderNo("ORDER-REQUIRED-001");
        order.setUserId(1L);
        order.setAmount(new BigDecimal("100.00"));
        order.setStatus(0);
        ordersMapper.insert(order);
        
        log.info("插入订单成功");
        
        // 调用另一个REQUIRED方法，会加入当前事务
        insertLog("REQUIRED", "订单创建成功");
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void insertLog(String type, String desc) {
        TransactionLog log = new TransactionLog();
        log.setOperationType(type);
        log.setDescription(desc);
        logMapper.insert(log);
        
        this.log.info("插入日志成功");
    }

    /**
     * REQUIRES_NEW：创建新事务，挂起当前事务
     * 
     * 应用场景：记录日志，即使主业务回滚，日志也要保存
     */
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void requiresNewDemo(boolean throwException) {
        log.info("=== REQUIRES_NEW 传播行为演示 ===");
        
        Orders order = new Orders();
        order.setOrderNo("ORDER-NEW-001");
        order.setUserId(1L);
        order.setAmount(new BigDecimal("200.00"));
        order.setStatus(0);
        ordersMapper.insert(order);
        
        log.info("插入订单成功");
        
        // 调用REQUIRES_NEW方法，会创建新事务
        // 即使外层事务回滚，这个日志也会保存
        insertLogWithNewTransaction("REQUIRES_NEW", "订单创建（可能回滚）");
        
        if (throwException) {
            log.warn("抛出异常，外层事务将回滚，但日志已在独立事务中提交");
            throw new RuntimeException("模拟异常");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void insertLogWithNewTransaction(String type, String desc) {
        TransactionLog log = new TransactionLog();
        log.setOperationType(type);
        log.setDescription(desc);
        logMapper.insert(log);
        
        this.log.info("在新事务中插入日志成功");
    }

    /**
     * NESTED：嵌套事务
     * 
     * 特点：
     * - 外层事务回滚，嵌套事务也会回滚
     * - 嵌套事务回滚，不影响外层事务（可以被捕获）
     * 
     * 实现原理：使用JDBC的Savepoint机制
     */
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void nestedDemo(boolean innerException, boolean outerException) {
        log.info("=== NESTED 传播行为演示 ===");
        
        Orders order = new Orders();
        order.setOrderNo("ORDER-NESTED-001");
        order.setUserId(1L);
        order.setAmount(new BigDecimal("300.00"));
        order.setStatus(0);
        ordersMapper.insert(order);
        
        log.info("外层事务：插入订单成功");
        
        try {
            // 调用嵌套事务
            insertLogWithNested("NESTED", "嵌套事务日志", innerException);
        } catch (Exception e) {
            log.warn("捕获嵌套事务异常，外层事务可以继续：{}", e.getMessage());
        }
        
        if (outerException) {
            log.warn("外层事务抛出异常，所有操作都会回滚");
            throw new RuntimeException("外层事务异常");
        }
        
        log.info("外层事务提交");
    }

    @Transactional(propagation = Propagation.NESTED, rollbackFor = Exception.class)
    public void insertLogWithNested(String type, String desc, boolean throwException) {
        TransactionLog log = new TransactionLog();
        log.setOperationType(type);
        log.setDescription(desc);
        logMapper.insert(log);
        
        this.log.info("嵌套事务：插入日志成功");
        
        if (throwException) {
            throw new RuntimeException("嵌套事务异常");
        }
    }

    /**
     * SUPPORTS：支持当前事务，如果不存在则以非事务方式执行
     */
    @Transactional(propagation = Propagation.SUPPORTS)
    public void supportsDemo() {
        log.info("=== SUPPORTS 传播行为演示 ===");
        log.info("如果在事务中调用，则加入事务；否则以非事务方式执行");
        
        TransactionLog log = new TransactionLog();
        log.setOperationType("SUPPORTS");
        log.setDescription("SUPPORTS演示");
        logMapper.insert(log);
    }

    /**
     * MANDATORY：必须在事务中执行，否则抛出异常
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void mandatoryDemo() {
        log.info("=== MANDATORY 传播行为演示 ===");
        log.info("必须在事务中调用，否则抛出异常");
        
        TransactionLog log = new TransactionLog();
        log.setOperationType("MANDATORY");
        log.setDescription("MANDATORY演示");
        logMapper.insert(log);
    }

    /**
     * NOT_SUPPORTED：以非事务方式执行，挂起当前事务
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void notSupportedDemo() {
        log.info("=== NOT_SUPPORTED 传播行为演示 ===");
        log.info("总是以非事务方式执行");
        
        TransactionLog log = new TransactionLog();
        log.setOperationType("NOT_SUPPORTED");
        log.setDescription("NOT_SUPPORTED演示");
        logMapper.insert(log);
    }

    /**
     * NEVER：不能在事务中执行，否则抛出异常
     */
    @Transactional(propagation = Propagation.NEVER)
    public void neverDemo() {
        log.info("=== NEVER 传播行为演示 ===");
        log.info("不能在事务中调用，否则抛出异常");
        
        TransactionLog log = new TransactionLog();
        log.setOperationType("NEVER");
        log.setDescription("NEVER演示");
        logMapper.insert(log);
    }
}
