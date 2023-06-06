package com.huabin.mybatisplus;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @Author huabin
 * @DateTime 2023-06-02 08:22
 * @Desc
 */

@SpringBootApplication
@MapperScan("com.huabin.mybatisplus.transaction.mapper")
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

}
