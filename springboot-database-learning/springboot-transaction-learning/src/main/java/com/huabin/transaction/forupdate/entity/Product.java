package com.huabin.transaction.forupdate.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品实体类
 * 用于演示FOR UPDATE在不同事务隔离级别下的行为
 * 
 * @author huabin
 */
@Data
public class Product {
    
    /**
     * 商品ID
     */
    private Long id;
    
    /**
     * 商品名称
     */
    private String name;
    
    /**
     * 商品价格
     */
    private BigDecimal price;
    
    /**
     * 库存数量
     */
    private Integer stock;
    
    /**
     * 版本号（乐观锁）
     */
    private Integer version;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updatedTime;
}
