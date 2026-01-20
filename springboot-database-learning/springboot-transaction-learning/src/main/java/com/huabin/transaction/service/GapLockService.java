package com.huabin.transaction.service;

import com.huabin.transaction.entity.User;
import com.huabin.transaction.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 间隙锁（Gap Lock）演示Service
 * 
 * 间隙锁是InnoDB在RR（可重复读）隔离级别下为了解决幻读问题而引入的锁机制
 * 
 * 间隙锁锁定的是索引记录之间的间隙，防止其他事务在间隙中插入数据
 * 
 * @author huabin
 */
@Slf4j
@Service
public class GapLockService {

    @Autowired
    private UserMapper userMapper;

    /**
     * 演示间隙锁
     * 
     * 场景：查询年龄在20-30之间的用户
     * 
     * 当前数据：age = 10, 20, 30, 50
     * 执行：SELECT * FROM user WHERE age BETWEEN 20 AND 30 FOR UPDATE
     * 
     * 会产生以下锁：
     * 1. 记录锁：age=20, age=30 的记录
     * 2. 间隙锁：(10, 20), (20, 30), (30, 50) 的间隙
     * 
     * 此时其他事务无法在这些间隙中插入数据，防止幻读
     */
    @Transactional(rollbackFor = Exception.class)
    public List<User> queryWithGapLock(Integer minAge, Integer maxAge) {
        log.info("使用间隙锁查询年龄范围：{} - {}", minAge, maxAge);
        
        // 范围查询并加锁，会产生间隙锁
        List<User> users = userMapper.selectByAgeRangeForUpdate(minAge, maxAge);
        log.info("查询到{}个用户", users.size());
        
        // 模拟业务处理，此时间隙被锁定
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        return users;
    }

    /**
     * 尝试在间隙中插入数据
     * 
     * 如果有其他事务持有间隙锁，此操作会被阻塞
     */
    @Transactional(rollbackFor = Exception.class)
    public void insertIntoGap(Integer age, String name, String email) {
        log.info("尝试插入用户：age={}, name={}", age, name);
        
        User user = new User();
        user.setAge(age);
        user.setName(name);
        user.setEmail(email);
        
        int rows = userMapper.insert(user);
        log.info("插入成功，影响行数：{}", rows);
    }

    /**
     * 演示Next-Key Lock（记录锁 + 间隙锁）
     * 
     * Next-Key Lock = Record Lock + Gap Lock
     * 
     * 它锁定一个范围，并且锁定记录本身
     * 这是InnoDB默认的行锁算法
     */
    @Transactional(rollbackFor = Exception.class)
    public void nextKeyLockDemo() {
        log.info("Next-Key Lock演示");
        
        // 假设当前有记录：age = 10, 20, 30, 50
        // 执行：SELECT * FROM user WHERE age >= 20 AND age < 40 FOR UPDATE
        // 
        // 会产生以下Next-Key Lock：
        // (10, 20], (20, 30], (30, 50)
        // 
        // 锁定范围：
        // - 记录锁：age=20, age=30
        // - 间隙锁：(10, 20), (20, 30), (30, 50)
        
        List<User> users = userMapper.selectByAgeRangeForUpdate(20, 40);
        log.info("查询结果：{}", users);
        
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
