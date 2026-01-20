package com.huabin.transaction;

import com.huabin.transaction.entity.Account;
import com.huabin.transaction.mapper.AccountMapper;
import com.huabin.transaction.service.*;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 事务测试类
 * 
 * @author huabin
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class TransactionTest {

    @Autowired
    private RowLockService rowLockService;
    
    @Autowired
    private GapLockService gapLockService;
    
    @Autowired
    private MvccService mvccService;
    
    @Autowired
    private TransactionPropagationService propagationService;
    
    @Autowired
    private TransactionFailureService failureService;
    
    @Autowired
    private AccountMapper accountMapper;

    /**
     * 测试排他锁
     */
    @Test
    public void testExclusiveLock() {
        try {
            rowLockService.transferWithExclusiveLock(1L, 2L, new BigDecimal("100"));
            log.info("转账成功");
        } catch (Exception e) {
            log.error("转账失败", e);
        }
    }

    /**
     * 测试共享锁
     */
    @Test
    public void testShareLock() {
        BigDecimal balance = rowLockService.queryBalanceWithShareLock(1L);
        log.info("账户余额：{}", balance);
    }

    /**
     * 测试乐观锁
     */
    @Test
    public void testOptimisticLock() {
        try {
            rowLockService.updateBalanceWithOptimisticLock(1L, new BigDecimal("5000"));
            log.info("更新成功");
        } catch (Exception e) {
            log.error("更新失败", e);
        }
    }

    /**
     * 测试并发乐观锁
     */
    @Test
    public void testConcurrentOptimisticLock() throws InterruptedException {
        int threadCount = 10;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    rowLockService.updateBalanceWithOptimisticLock(1L, 
                        new BigDecimal("1000").add(new BigDecimal(index)));
                    log.info("线程{}更新成功", index);
                } catch (Exception e) {
                    log.error("线程{}更新失败：{}", index, e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        executor.shutdown();
        
        Account account = accountMapper.selectById(1L);
        log.info("最终余额：{}，版本号：{}", account.getBalance(), account.getVersion());
    }

    /**
     * 测试死锁
     * 
     * 注意：需要在两个不同的线程中执行
     */
    @Test
    public void testDeadlock() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);
        
        // 线程1：先锁账户1，再锁账户2
        new Thread(() -> {
            try {
                rowLockService.deadlockDemo1(1L, 2L);
                log.info("线程1执行成功");
            } catch (Exception e) {
                log.error("线程1发生死锁", e);
            } finally {
                latch.countDown();
            }
        }).start();
        
        // 线程2：先锁账户2，再锁账户1
        new Thread(() -> {
            try {
                rowLockService.deadlockDemo2(1L, 2L);
                log.info("线程2执行成功");
            } catch (Exception e) {
                log.error("线程2发生死锁", e);
            } finally {
                latch.countDown();
            }
        }).start();
        
        latch.await();
    }

    /**
     * 测试间隙锁
     */
    @Test
    public void testGapLock() {
        gapLockService.queryWithGapLock(20, 30);
    }

    /**
     * 测试MVCC - READ COMMITTED
     */
    @Test
    public void testReadCommitted() {
        mvccService.readCommittedDemo(1L);
    }

    /**
     * 测试MVCC - REPEATABLE READ
     */
    @Test
    public void testRepeatableRead() {
        mvccService.repeatableReadDemo(1L);
    }

    /**
     * 测试当前读vs快照读
     */
    @Test
    public void testCurrentVsSnapshot() {
        mvccService.currentReadVsSnapshotRead(1L);
    }

    /**
     * 测试REQUIRED传播行为
     */
    @Test
    public void testRequired() {
        propagationService.requiredDemo();
    }

    /**
     * 测试REQUIRES_NEW传播行为
     */
    @Test
    public void testRequiresNew() {
        try {
            propagationService.requiresNewDemo(true);
        } catch (Exception e) {
            log.error("外层事务回滚，但日志已保存", e);
        }
    }

    /**
     * 测试NESTED传播行为
     */
    @Test
    public void testNested() {
        // 内层异常，外层捕获
        propagationService.nestedDemo(true, false);
        
        // 外层异常，全部回滚
        try {
            propagationService.nestedDemo(false, true);
        } catch (Exception e) {
            log.error("外层事务回滚", e);
        }
    }

    /**
     * 测试事务失效 - 自调用
     */
    @Test
    public void testSelfInvocation() {
        try {
            failureService.selfInvocationFailure();
        } catch (Exception e) {
            log.error("自调用导致事务失效", e);
        }
    }

    /**
     * 测试事务失效 - 异常被捕获
     */
    @Test
    public void testExceptionCaught() {
        failureService.exceptionCaughtFailure();
        
        // 检查数据是否被修改
        Account account = accountMapper.selectById(1L);
        log.info("账户余额：{}", account.getBalance());
    }

    /**
     * 测试正确的事务使用
     */
    @Test
    public void testCorrectUsage() {
        failureService.correctTransactionUsage();
    }

    /**
     * 并发转账测试
     */
    @Test
    public void testConcurrentTransfer() throws InterruptedException {
        // 初始化账户余额
        accountMapper.updateBalance(1L, new BigDecimal("10000"));
        accountMapper.updateBalance(2L, new BigDecimal("10000"));
        
        int threadCount = 10;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        // 10个线程同时从账户1转账100到账户2
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    rowLockService.transferWithExclusiveLock(1L, 2L, new BigDecimal("100"));
                    log.info("线程{}转账成功", index);
                } catch (Exception e) {
                    log.error("线程{}转账失败", index, e);
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        executor.shutdown();
        
        // 验证余额
        Account account1 = accountMapper.selectById(1L);
        Account account2 = accountMapper.selectById(2L);
        
        log.info("账户1余额：{}（预期：9000）", account1.getBalance());
        log.info("账户2余额：{}（预期：11000）", account2.getBalance());
        
        // 断言
        assert account1.getBalance().compareTo(new BigDecimal("9000")) == 0;
        assert account2.getBalance().compareTo(new BigDecimal("11000")) == 0;
    }
}
