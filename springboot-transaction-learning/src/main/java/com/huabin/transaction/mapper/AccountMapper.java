package com.huabin.transaction.mapper;

import com.huabin.transaction.entity.Account;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

/**
 * 账户Mapper
 * 
 * @author huabin
 */
public interface AccountMapper {
    
    /**
     * 根据ID查询账户（普通查询）
     */
    Account selectById(@Param("id") Long id);
    
    /**
     * 根据ID查询账户（加排他锁 FOR UPDATE）
     */
    Account selectByIdForUpdate(@Param("id") Long id);
    
    /**
     * 根据ID查询账户（加共享锁 LOCK IN SHARE MODE）
     */
    Account selectByIdLockInShareMode(@Param("id") Long id);
    
    /**
     * 更新账户余额
     */
    int updateBalance(@Param("id") Long id, @Param("balance") BigDecimal balance);
    
    /**
     * 更新账户余额（乐观锁版本号）
     */
    int updateBalanceWithVersion(@Param("id") Long id, 
                                  @Param("balance") BigDecimal balance, 
                                  @Param("version") Integer version);
    
    /**
     * 扣减余额
     */
    int deductBalance(@Param("id") Long id, @Param("amount") BigDecimal amount);
    
    /**
     * 增加余额
     */
    int addBalance(@Param("id") Long id, @Param("amount") BigDecimal amount);
    
    /**
     * 插入账户
     */
    int insert(Account account);
}
