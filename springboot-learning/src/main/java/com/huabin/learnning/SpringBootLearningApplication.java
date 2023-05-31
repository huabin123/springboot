package com.huabin.learnning;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @Author huabin
 * @DateTime 2023-05-26 10:32
 * @Desc
 */

@SpringBootApplication
public class SpringBootLearningApplication {

    public static void main(String[] args) {
//        SpringApplication.run(SpringBootLearningApplication.class, args);

        SpringApplication springApplication = new SpringApplication(SpringBootLearningApplication.class);

        springApplication.run(args);
    }

}
