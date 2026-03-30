package com.huabin.consumer.dto;

import java.io.Serializable;

/**
 * 用户 DTO - 修复后的版本（添加了无参构造方法）
 * 
 * ✅ 解决方案：添加无参构造方法
 * 这样 Feign 调用时 JSON 反序列化就能成功
 */
public class UserDTOFixed implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String username;
    private String email;
    private Integer age;

    /**
     * ✅ 无参构造方法（必须！）
     * JSON 反序列化框架需要通过无参构造方法创建对象实例
     */
    public UserDTOFixed() {
    }

    /**
     * 有参构造方法（可选，便于创建对象）
     */
    public UserDTOFixed(Long id, String username, String email, Integer age) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.age = age;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    @Override
    public String toString() {
        return "UserDTOFixed{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", email='" + email + '\'' +
                ", age=" + age +
                '}';
    }
}
