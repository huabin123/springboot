package com.huabin.transaction;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * MySQL事务和Spring事务学习应用
 * 
 * @author huabin
 */
@SpringBootApplication
@EnableTransactionManagement
@MapperScan("com.huabin.transaction.mapper")
public class TransactionLearningApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransactionLearningApplication.class, args);
    }
}
