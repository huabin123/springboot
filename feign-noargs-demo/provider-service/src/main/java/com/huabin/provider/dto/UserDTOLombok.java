package com.huabin.provider.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 用户 DTO - Lombok 版本（推荐）
 * 
 * ✅ 最佳实践：使用 Lombok 注解自动生成构造方法
 * @NoArgsConstructor - 生成无参构造方法
 * @AllArgsConstructor - 生成全参构造方法
 * @Data - 生成 getter/setter/toString/equals/hashCode
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDTOLombok implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String username;
    private String email;
    private Integer age;
}
