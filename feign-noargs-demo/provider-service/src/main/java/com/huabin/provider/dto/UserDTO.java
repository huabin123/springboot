package com.huabin.provider.dto;

import java.io.Serializable;

/**
 * 用户 DTO - 有问题的版本（只有有参构造方法）
 * 
 * ❌ 问题：只定义了有参构造方法，没有无参构造方法
 * 这会导致 Feign 调用时 JSON 反序列化失败
 */
public class UserDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String username;
    private String email;
    private Integer age;

    /**
     * 有参构造方法
     * ⚠️ 一旦定义了有参构造，编译器就不会提供默认的无参构造方法
     */
    public UserDTO(Long id, String username, String email, Integer age) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.age = age;
    }

    // ❌ 缺少无参构造方法！
    // public UserDTO() {}

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
        return "UserDTO{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", email='" + email + '\'' +
                ", age=" + age +
                '}';
    }
}
