package com.huabin.transaction.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单实体
 * 
 * @author huabin
 */
@Data
public class Orders {
    
    private Long id;
    
    private String orderNo;
    
    private Long userId;
    
    private BigDecimal amount;
    
    private Integer status;
    
    private LocalDateTime createdTime;
    
    private LocalDateTime updatedTime;
}
