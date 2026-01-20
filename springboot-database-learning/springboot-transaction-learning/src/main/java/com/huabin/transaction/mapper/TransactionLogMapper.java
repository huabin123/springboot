package com.huabin.transaction.mapper;

import com.huabin.transaction.entity.TransactionLog;

/**
 * 事务日志Mapper
 * 
 * @author huabin
 */
public interface TransactionLogMapper {
    
    /**
     * 插入日志
     */
    int insert(TransactionLog log);
}
