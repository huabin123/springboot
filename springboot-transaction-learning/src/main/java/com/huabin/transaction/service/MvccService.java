package com.huabin.transaction.service;

import com.huabin.transaction.entity.Account;
import com.huabin.transaction.mapper.AccountMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * MVCC（多版本并发控制）演示Service
 * 
 * MVCC是MySQL InnoDB实现事务隔离级别的核心机制
 * 
 * 核心概念：
 * 1. 每行数据都有隐藏字段：DB_TRX_ID（事务ID）、DB_ROLL_PTR（回滚指针）
 * 2. Undo Log：保存数据的历史版本
 * 3. Read View：事务开始时创建的一致性视图
 * 
 * 工作原理：
 * - READ COMMITTED：每次查询都创建新的Read View
 * - REPEATABLE READ：事务开始时创建Read View，整个事务期间使用同一个
 * 
 * @author huabin
 */
@Slf4j
@Service
public class MvccService {

    @Autowired
    private AccountMapper accountMapper;

    /**
     * 演示READ COMMITTED隔离级别下的MVCC
     * 
     * 特点：每次SELECT都会创建新的Read View
     * 结果：可以读取到其他事务已提交的修改（不可重复读）
     */
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public void readCommittedDemo(Long accountId) {
        log.info("=== READ COMMITTED 隔离级别演示 ===");
        
        // 第一次查询
        Account account1 = accountMapper.selectById(accountId);
        log.info("第一次查询余额：{}", account1.getBalance());
        
        // 模拟等待其他事务修改数据
        log.info("等待5秒，期间其他事务可能会修改数据...");
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        // 第二次查询（会创建新的Read View）
        Account account2 = accountMapper.selectById(accountId);
        log.info("第二次查询余额：{}", account2.getBalance());
        
        // 在READ COMMITTED级别下，两次查询结果可能不同（不可重复读）
        if (!account1.getBalance().equals(account2.getBalance())) {
            log.warn("发生了不可重复读！第一次：{}，第二次：{}", 
                    account1.getBalance(), account2.getBalance());
        }
    }

    /**
     * 演示REPEATABLE READ隔离级别下的MVCC
     * 
     * 特点：事务开始时创建Read View，整个事务期间使用同一个
     * 结果：多次查询结果一致（可重复读），看不到其他事务的修改
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ, rollbackFor = Exception.class)
    public void repeatableReadDemo(Long accountId) {
        log.info("=== REPEATABLE READ 隔离级别演示 ===");
        
        // 第一次查询（创建Read View）
        Account account1 = accountMapper.selectById(accountId);
        log.info("第一次查询余额：{}", account1.getBalance());
        
        // 模拟等待其他事务修改数据
        log.info("等待5秒，期间其他事务可能会修改数据...");
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        // 第二次查询（使用同一个Read View）
        Account account2 = accountMapper.selectById(accountId);
        log.info("第二次查询余额：{}", account2.getBalance());
        
        // 在REPEATABLE READ级别下，两次查询结果应该相同
        if (account1.getBalance().equals(account2.getBalance())) {
            log.info("实现了可重复读！两次查询结果一致：{}", account1.getBalance());
        } else {
            log.warn("异常：两次查询结果不一致");
        }
    }

    /**
     * 演示当前读 vs 快照读
     * 
     * 快照读：普通的SELECT语句，读取的是快照数据（通过MVCC实现）
     * 当前读：SELECT ... FOR UPDATE、UPDATE、DELETE等，读取的是最新数据
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ, rollbackFor = Exception.class)
    public void currentReadVsSnapshotRead(Long accountId) {
        log.info("=== 当前读 vs 快照读 ===");
        
        // 快照读
        Account snapshot = accountMapper.selectById(accountId);
        log.info("快照读结果：{}", snapshot.getBalance());
        
        // 等待其他事务修改
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        // 再次快照读（结果应该和第一次相同）
        Account snapshot2 = accountMapper.selectById(accountId);
        log.info("第二次快照读：{}", snapshot2.getBalance());
        
        // 当前读（会读取最新数据，并加锁）
        Account current = accountMapper.selectByIdForUpdate(accountId);
        log.info("当前读结果：{}", current.getBalance());
        
        log.info("快照读看到的是事务开始时的数据，当前读看到的是最新数据");
    }
}
