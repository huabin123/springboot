package com.huabin.transaction.service;

import com.huabin.transaction.entity.Account;
import com.huabin.transaction.mapper.AccountMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 行锁演示Service
 * 
 * MySQL InnoDB的行锁是通过给索引上的索引项加锁来实现的
 * 
 * @author huabin
 */
@Slf4j
@Service
public class RowLockService {

    @Autowired
    private AccountMapper accountMapper;

    /**
     * 演示排他锁（X锁）- FOR UPDATE
     * 
     * 场景：转账操作，需要锁定账户防止并发修改
     * 
     * 使用方式：SELECT ... FOR UPDATE
     * 特点：其他事务不能读取（加锁读）也不能修改
     */
    @Transactional(rollbackFor = Exception.class)
    public void transferWithExclusiveLock(Long fromAccountId, Long toAccountId, BigDecimal amount) {
        log.info("开始转账：从账户{}转账{}到账户{}", fromAccountId, amount, toAccountId);
        
        // 1. 使用FOR UPDATE锁定转出账户（排他锁）
        Account fromAccount = accountMapper.selectByIdForUpdate(fromAccountId);
        log.info("锁定转出账户：{}", fromAccount);
        
        // 2. 检查余额
        if (fromAccount.getBalance().compareTo(amount) < 0) {
            throw new RuntimeException("余额不足");
        }
        
        // 3. 锁定转入账户
        Account toAccount = accountMapper.selectByIdForUpdate(toAccountId);
        log.info("锁定转入账户：{}", toAccount);
        
        // 4. 执行转账
        accountMapper.deductBalance(fromAccountId, amount);
        accountMapper.addBalance(toAccountId, amount);
        
        log.info("转账成功");
        
        // 模拟业务处理时间，此时其他事务无法访问这两个账户
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * 演示共享锁（S锁）- LOCK IN SHARE MODE
     * 
     * 场景：查询账户余额，允许其他事务读取，但不允许修改
     * 
     * 使用方式：SELECT ... LOCK IN SHARE MODE
     * 特点：多个事务可以同时持有共享锁，但不能修改数据
     */
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal queryBalanceWithShareLock(Long accountId) {
        log.info("使用共享锁查询账户余额：{}", accountId);
        
        // 使用共享锁查询
        Account account = accountMapper.selectByIdLockInShareMode(accountId);
        log.info("查询到账户：{}", account);
        
        // 模拟业务处理
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        return account.getBalance();
    }

    /**
     * 演示乐观锁
     * 
     * 场景：使用版本号实现乐观锁，适合读多写少的场景
     * 
     * 实现方式：通过version字段判断数据是否被修改
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateBalanceWithOptimisticLock(Long accountId, BigDecimal newBalance) {
        log.info("使用乐观锁更新账户余额：accountId={}, newBalance={}", accountId, newBalance);
        
        // 1. 查询当前数据和版本号
        Account account = accountMapper.selectById(accountId);
        log.info("当前账户信息：{}", account);
        
        // 2. 模拟业务处理
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        // 3. 更新时检查版本号
        int rows = accountMapper.updateBalanceWithVersion(accountId, newBalance, account.getVersion());
        
        if (rows == 0) {
            throw new RuntimeException("更新失败，数据已被其他事务修改（乐观锁冲突）");
        }
        
        log.info("乐观锁更新成功");
    }

    /**
     * 演示死锁场景
     * 
     * 两个事务互相等待对方持有的锁，造成死锁
     * MySQL会自动检测死锁并回滚其中一个事务
     */
    @Transactional(rollbackFor = Exception.class)
    public void deadlockDemo1(Long account1Id, Long account2Id) {
        log.info("死锁演示1：先锁定账户{}，再锁定账户{}", account1Id, account2Id);
        
        // 先锁定账户1
        Account account1 = accountMapper.selectByIdForUpdate(account1Id);
        log.info("已锁定账户1：{}", account1);
        
        // 休眠，让另一个事务有机会锁定账户2
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        // 尝试锁定账户2（可能造成死锁）
        log.info("尝试锁定账户2...");
        Account account2 = accountMapper.selectByIdForUpdate(account2Id);
        log.info("已锁定账户2：{}", account2);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deadlockDemo2(Long account1Id, Long account2Id) {
        log.info("死锁演示2：先锁定账户{}，再锁定账户{}", account2Id, account1Id);
        
        // 先锁定账户2
        Account account2 = accountMapper.selectByIdForUpdate(account2Id);
        log.info("已锁定账户2：{}", account2);
        
        // 休眠
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        // 尝试锁定账户1（可能造成死锁）
        log.info("尝试锁定账户1...");
        Account account1 = accountMapper.selectByIdForUpdate(account1Id);
        log.info("已锁定账户1：{}", account1);
    }
}
