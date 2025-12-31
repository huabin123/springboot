package com.huabin.multids;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * @Author huabin
 * @DateTime 2025-12-29
 * @Desc MyBatis 多数据源应用启动类
 *
 * 关键配置说明：
 * 1. @SpringBootApplication：标记为Spring Boot应用
 * 2. exclude = {DataSourceAutoConfiguration.class}：排除数据源自动配置
 *    - 因为我们使用自定义的多数据源配置
 *    - 如果不排除，Spring Boot会尝试自动配置单数据源，导致冲突
 *
 * 注意事项：
 * - 不要在启动类上使用@MapperScan注解
 * - 所有@MapperScan配置都在数据源配置类中完成
 * - 主数据源配置：PrimaryDataSourceConfig
 * - 从数据源配置：SecondaryDataSourceConfig
 *
 * 项目结构：
 * - com.huabin.multids.config：配置类
 * - com.huabin.multids.db1.mapper：主数据源Mapper
 * - com.huabin.multids.db1.entity：主数据源实体类
 * - com.huabin.multids.db2.mapper：从数据源Mapper
 * - com.huabin.multids.db2.entity：从数据源实体类
 * - com.huabin.multids.service：业务逻辑层
 * - com.huabin.multids.controller：控制器层
 */
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class MultiDataSourceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MultiDataSourceApplication.class, args);
        System.out.println("\n========================================");
        System.out.println("MyBatis 多数据源应用启动成功！");
        System.out.println("========================================");
        System.out.println("主数据源：springboot_db");
        System.out.println("  - Mapper包：com.huabin.multids.db1.mapper");
        System.out.println("  - XML路径：classpath:mapper/db1/*.xml");
        System.out.println("从数据源：springboot_db2");
        System.out.println("  - Mapper包：com.huabin.multids.db2.mapper");
        System.out.println("  - XML路径：classpath:mapper/db2/*.xml");
        System.out.println("========================================");
        System.out.println("访问地址：http://localhost:8080");
        System.out.println("========================================\n");
    }
}
