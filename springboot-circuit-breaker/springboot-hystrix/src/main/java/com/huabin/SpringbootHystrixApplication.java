package com.huabin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.circuitbreaker.EnableCircuitBreaker;
import org.springframework.cloud.netflix.hystrix.EnableHystrix;

/**
 * @Author huabin
 * @DateTime 2022-12-28 15:43
 * @Desc
 */

@SpringBootApplication
@EnableCircuitBreaker
@EnableHystrix
public class SpringbootHystrixApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringbootHystrixApplication.class, args);
    }

}
