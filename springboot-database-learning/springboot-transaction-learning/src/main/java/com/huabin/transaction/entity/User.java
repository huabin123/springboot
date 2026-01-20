package com.huabin.transaction.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户实体
 * 
 * @author huabin
 */
@Data
public class User {
    
    private Long id;
    
    private Integer age;
    
    private String name;
    
    private String email;
    
    private LocalDateTime createdTime;
}
