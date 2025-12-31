package com.huabin.transaction.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 事务日志实体
 * 
 * @author huabin
 */
@Data
public class TransactionLog {
    
    private Long id;
    
    private String operationType;
    
    private String description;
    
    private LocalDateTime createdTime;
}
