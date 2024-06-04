package com.huabin.sharding;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @Author huabin
 * @DateTime 2024-05-30 10:06
 * @Desc
 */
@SpringBootApplication
@MapperScan("com.huabin.sharding.order.mapper")
public class ShardingApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShardingApplication.class, args);
    }

}
