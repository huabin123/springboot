package com.huabin.generator;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * @Author huabin
 * @DateTime 2023-04-25 17:52
 * @Desc
 */

@SpringBootApplication
public class SpringbootMybatisGeneratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringbootMybatisGeneratorApplication.class, args);
    }

}
