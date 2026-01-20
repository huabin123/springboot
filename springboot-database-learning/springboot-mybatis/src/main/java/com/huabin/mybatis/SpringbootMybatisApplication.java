package com.huabin.mybatis;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @Author huabin
 * @DateTime 2023-04-24 10:09
 * @Desc MyBatis示例应用
 * 
 * 关键配置说明：
 * 1. @MapperScan：扫描指定包下的所有Mapper接口，自动注册为Spring Bean
 * 2. 如果不使用@MapperScan，需要在每个Mapper接口上添加@Mapper注解
 */

@SpringBootApplication
@MapperScan("com.huabin.mybatis.mapper")  // 扫描mapper包，使Mapper接口能够被自动注入
public class SpringbootMybatisApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringbootMybatisApplication.class, args);
    }

}
