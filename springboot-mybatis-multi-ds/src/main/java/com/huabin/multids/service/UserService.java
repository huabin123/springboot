package com.huabin.multids.service;

import com.huabin.multids.db1.entity.User;
import com.huabin.multids.db1.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @Author huabin
 * @DateTime 2025-12-29
 * @Desc 用户业务逻辑层（使用主数据源 springboot_db）
 * 
 * 关键说明：
 * 1. 注入的是 db1.mapper.UserMapper，自动使用主数据源
 * 2. @Transactional 默认使用主数据源的事务管理器（primaryTransactionManager）
 * 3. 如果需要明确指定事务管理器，使用：@Transactional(transactionManager = "primaryTransactionManager")
 */
@Service
public class UserService {

    /**
     * 注入主数据源的 UserMapper
     * 
     * 说明：
     * - 由于 UserMapper 在 com.huabin.multids.db1.mapper 包下
     * - PrimaryDataSourceConfig 配置了扫描此包
     * - 因此自动使用主数据源（springboot_db）
     */
    @Autowired
    private UserMapper userMapper;

    /**
     * 根据ID查询用户
     */
    public User getUserById(Long id) {
        return userMapper.selectByPrimaryKey(id);
    }

    /**
     * 查询所有用户
     */
    public List<User> getAllUsers() {
        return userMapper.selectAll();
    }

    /**
     * 根据用户名查询
     */
    public User getUserByUsername(String username) {
        return userMapper.selectByUsername(username);
    }

    /**
     * 根据条件查询用户列表
     */
    public List<User> getUsersByCondition(String username, Integer status) {
        return userMapper.selectByCondition(username, status);
    }

    /**
     * 创建用户（带事务）
     * 
     * 事务说明：
     * - @Transactional 默认使用 primaryTransactionManager
     * - 如果需要明确指定：@Transactional(transactionManager = "primaryTransactionManager")
     */
    @Transactional(rollbackFor = Exception.class)
    public int createUser(User user) {
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        return userMapper.insert(user);
    }

    /**
     * 更新用户（带事务）
     */
    @Transactional(rollbackFor = Exception.class)
    public int updateUser(User user) {
        user.setUpdateTime(LocalDateTime.now());
        return userMapper.updateByPrimaryKey(user);
    }

    /**
     * 删除用户（带事务）
     */
    @Transactional(rollbackFor = Exception.class)
    public int deleteUser(Long id) {
        return userMapper.deleteByPrimaryKey(id);
    }

    /**
     * 批量创建用户（带事务）
     * 
     * 说明：
     * - 整个方法在一个事务中执行
     * - 任何一个用户创建失败，所有操作都会回滚
     */
    @Transactional(rollbackFor = Exception.class)
    public int batchCreateUsers(List<User> users) {
        int count = 0;
        for (User user : users) {
            user.setCreateTime(LocalDateTime.now());
            user.setUpdateTime(LocalDateTime.now());
            count += userMapper.insert(user);
        }
        return count;
    }
}
