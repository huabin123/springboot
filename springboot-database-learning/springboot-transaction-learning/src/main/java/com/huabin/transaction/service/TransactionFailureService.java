package com.huabin.transaction.service;

import com.huabin.transaction.entity.Account;
import com.huabin.transaction.entity.TransactionLog;
import com.huabin.transaction.mapper.AccountMapper;
import com.huabin.transaction.mapper.TransactionLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Spring事务失效场景演示Service
 *
 * 常见的事务失效场景：
 * 1. 方法不是public
 * 2. 方法被final修饰
 * 3. 同一个类中方法调用（自调用）
 * 4. 异常被捕获未抛出
 * 5. 异常类型不匹配（默认只回滚RuntimeException和Error）
 * 6. 数据库引擎不支持事务（如MyISAM）
 * 7. 未被Spring管理
 *
 * @author huabin
 */
@Slf4j
@Service
public class TransactionFailureService {

    @Autowired
    private AccountMapper accountMapper;

    @Autowired
    private TransactionLogMapper logMapper;

    @Autowired
    private TransactionFailureService self; // 注入自己，用于解决自调用问题

    /**
     * 场景1：同一个类中方法调用（自调用）导致事务失效
     *
     * 原因：Spring事务是基于AOP代理实现的，自调用时不会经过代理对象
     *
     * 解决方案：
     * 1. 将被调用方法移到另一个Service
     * 2. 注入自己（self），通过self调用
     * 3. 使用AopContext.currentProxy()获取代理对象
     */
    @Transactional(rollbackFor = Exception.class)
    public void selfInvocationFailure() {
        log.info("=== 场景1：自调用导致事务失效 ===");

        Account account = accountMapper.selectById(1L);
        log.info("当前余额：{}", account.getBalance());

        // 错误方式：直接调用，不会开启事务
        this.updateBalanceWithoutTransaction(1L, new BigDecimal("999.99"));

        // 正确方式：通过代理对象调用
        // self.updateBalanceWithTransaction(1L, new BigDecimal("999.99"));
    }

    /**
     * 这个方法的@Transactional不会生效（如果被同类方法直接调用）
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateBalanceWithoutTransaction(Long accountId, BigDecimal balance) {
        log.info("更新账户余额（事务可能失效）");
        accountMapper.updateBalance(accountId, balance);

        // 抛出异常，但如果是自调用，不会回滚
        throw new RuntimeException("模拟异常");
    }

    /**
     * 场景2：异常被捕获未抛出
     *
     * 原因：Spring事务只有在捕获到异常时才会回滚
     */
    @Transactional(rollbackFor = Exception.class)
    public void exceptionCaughtFailure() {
        log.info("=== 场景2：异常被捕获导致事务不回滚 ===");

        try {
            accountMapper.updateBalance(1L, new BigDecimal("888.88"));

            // 抛出异常
            throw new RuntimeException("模拟异常");

        } catch (Exception e) {
            // 异常被捕获，事务不会回滚
            log.error("捕获异常：{}", e.getMessage());
            // 正确做法：重新抛出异常
            // throw e;
        }

        log.warn("事务不会回滚，数据已被修改");
    }

    /**
     * 场景3：异常类型不匹配
     *
     * 原因：@Transactional默认只回滚RuntimeException和Error
     * 对于受检异常（Checked Exception），需要显式指定
     */
    @Transactional // 未指定rollbackFor
    public void wrongExceptionTypeFailure() throws Exception {
        log.info("=== 场景3：异常类型不匹配 ===");

        accountMapper.updateBalance(1L, new BigDecimal("777.77"));

        // 抛出受检异常，但未在rollbackFor中指定，不会回滚
        throw new Exception("受检异常");
    }

    /**
     * 正确做法：指定rollbackFor
     */
    @Transactional(rollbackFor = Exception.class)
    public void correctExceptionHandling() throws Exception {
        log.info("=== 正确处理异常 ===");

        accountMapper.updateBalance(1L, new BigDecimal("666.66"));

        // 抛出受检异常，会回滚
        throw new Exception("受检异常");
    }

    /**
     * 场景4：方法不是public
     *
     * 原因：Spring AOP默认只代理public方法
     */
    @Transactional(rollbackFor = Exception.class)
    private void privateMethodFailure() {
        log.info("=== 场景4：private方法事务失效 ===");

        accountMapper.updateBalance(1L, new BigDecimal("555.55"));

        throw new RuntimeException("private方法异常");
    }

    /**
     * 场景5：多线程调用
     *
     * 原因：Spring事务是基于ThreadLocal实现的，不同线程无法共享事务
     */
    @Transactional(rollbackFor = Exception.class)
    public void multiThreadFailure() {
        log.info("=== 场景5：多线程导致事务失效 ===");

        accountMapper.updateBalance(1L, new BigDecimal("444.44"));

        // 在新线程中执行数据库操作，不在同一个事务中
        new Thread(() -> {
            try {
                accountMapper.updateBalance(2L, new BigDecimal("333.33"));
                log.info("子线程执行完成");
            } catch (Exception e) {
                log.error("子线程异常", e);
            }
        }).start();

        // 主线程抛出异常，只回滚主线程的操作
        throw new RuntimeException("主线程异常");
    }

    /**
     * 场景6：事务传播类型设置错误
     */
    @Transactional(rollbackFor = Exception.class)
    public void propagationFailure() {
        log.info("=== 场景6：事务传播类型设置错误 ===");

        accountMapper.updateBalance(1L, new BigDecimal("222.22"));

        // 调用REQUIRES_NEW的方法，会在新事务中执行
        // 即使外层事务回滚，内层事务也已提交
        insertLogInNewTransaction();

        throw new RuntimeException("外层事务异常");
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void insertLogInNewTransaction() {
        TransactionLog log = new TransactionLog();
        log.setOperationType("REQUIRES_NEW");
        log.setDescription("新事务中的日志（不会回滚）");
        logMapper.insert(log);

        this.log.info("在新事务中插入日志");
    }

    /**
     * 演示正确的事务使用方式
     */
    @Transactional(rollbackFor = Exception.class)
    public void correctTransactionUsage() {
        log.info("=== 正确的事务使用方式 ===");

        try {
            // 1. 业务操作
            Account account = accountMapper.selectById(1L);
            accountMapper.updateBalance(1L, account.getBalance().add(new BigDecimal("100")));

            // 2. 记录日志
            TransactionLog log = new TransactionLog();
            log.setOperationType("TRANSFER");
            log.setDescription("转账成功");
            logMapper.insert(log);

            // 3. 如果需要在异常时也保存日志，使用REQUIRES_NEW
            // self.insertLogInNewTransaction();

            this.log.info("事务执行成功");

        } catch (Exception e) {
            log.error("事务执行失败", e);
            // 重新抛出异常，让Spring处理事务回滚
            throw e;
        }
    }
}
