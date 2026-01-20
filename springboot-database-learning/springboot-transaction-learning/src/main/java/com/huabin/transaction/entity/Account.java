package com.huabin.transaction.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 账户实体
 * 
 * @author huabin
 */
@Data
public class Account {
    
    private Long id;
    
    private String userName;
    
    private BigDecimal balance;
    
    private Integer version;
    
    private LocalDateTime createdTime;
    
    private LocalDateTime updatedTime;
}
