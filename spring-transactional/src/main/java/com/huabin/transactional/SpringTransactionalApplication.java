package com.huabin.transactional;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @Author huabin
 * @DateTime 2023-05-24 17:37
 * @Desc
 */

@MapperScan("com.huabin.transactional.mapper")
@SpringBootApplication
public class SpringTransactionalApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringTransactionalApplication.class, args);
    }

}
