# Eureka端口鉴权实战

## 一、项目概述

### 1.1 项目目标

本项目将实现一个完整的Eureka端口鉴权方案，包括：

```
1. Eureka Server端口鉴权配置
   - 启用Spring Security
   - 配置HTTP Basic认证
   - 自定义安全规则

2. Eureka Client连接配置
   - 服务提供者配置
   - 服务消费者配置
   - 认证信息配置

3. 高可用集群部署
   - 多节点Eureka Server
   - 集群间认证
   - 负载均衡

4. 安全加固
   - HTTPS配置
   - 密码加密
   - 访问控制
```

### 1.2 技术栈

```
- JDK 1.8
- Spring Boot 2.2.5.RELEASE
- Spring Cloud Hoxton.SR3
- Spring Cloud Netflix Eureka 2.2.5.RELEASE
- Spring Security
- Maven 3.6+
```

### 1.3 项目结构

```
springboot-eureka-learning/
├── eureka-server/                    # Eureka服务端
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   │   └── com/huabin/eureka/server/
│   │   │   │       ├── EurekaServerApplication.java
│   │   │   │       └── config/
│   │   │   │           └── WebSecurityConfig.java
│   │   │   └── resources/
│   │   │       ├── application.yml
│   │   │       └── application-peer1.yml
│   │   └── test/
│   └── pom.xml
│
├── eureka-client-producer/           # 服务提供者
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   │   └── com/huabin/eureka/producer/
│   │   │   │       ├── ProducerApplication.java
│   │   │   │       └── controller/
│   │   │   │           └── HelloController.java
│   │   │   └── resources/
│   │   │       └── application.yml
│   │   └── test/
│   └── pom.xml
│
├── eureka-client-consumer/           # 服务消费者
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   │   └── com/huabin/eureka/consumer/
│   │   │   │       ├── ConsumerApplication.java
│   │   │   │       └── controller/
│   │   │   │           └── ConsumerController.java
│   │   │   └── resources/
│   │   │       └── application.yml
│   │   └── test/
│   └── pom.xml
│
├── docs/                             # 文档目录
│   ├── 01-Eureka核心概念与架构.md
│   ├── 02-Eureka安全认证机制.md
│   ├── 03-Eureka端口鉴权实战.md
│   ├── 04-Eureka高可用集群部署.md
│   ├── 05-Eureka源码分析.md
│   └── README.md
│
└── pom.xml                           # 父POM
```

---

## 二、父POM配置

### 2.1 创建父POM

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.huabin</groupId>
    <artifactId>springboot-eureka-learning</artifactId>
    <version>1.0.0</version>
    <packaging>pom</packaging>

    <name>springboot-eureka-learning</name>
    <description>Spring Cloud Eureka深度学习项目</description>

    <modules>
        <module>eureka-server</module>
        <module>eureka-client-producer</module>
        <module>eureka-client-consumer</module>
    </modules>

    <properties>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
        <java.version>1.8</java.version>
        <maven.compiler.source>1.8</maven.compiler.source>
        <maven.compiler.target>1.8</maven.compiler.target>
        
        <!-- Spring Boot版本 -->
        <spring-boot.version>2.2.5.RELEASE</spring-boot.version>
        
        <!-- Spring Cloud版本 -->
        <spring-cloud.version>Hoxton.SR3</spring-cloud.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <!-- Spring Boot依赖管理 -->
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId>
                <version>${spring-boot.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>

            <!-- Spring Cloud依赖管理 -->
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring-cloud.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.8.1</version>
                <configuration>
                    <source>1.8</source>
                    <target>1.8</target>
                    <encoding>UTF-8</encoding>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

---

## 三、Eureka Server实现

### 3.1 POM配置

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.huabin</groupId>
        <artifactId>springboot-eureka-learning</artifactId>
        <version>1.0.0</version>
    </parent>

    <artifactId>eureka-server</artifactId>
    <name>eureka-server</name>
    <description>Eureka服务端（带鉴权）</description>

    <dependencies>
        <!-- Eureka Server -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-server</artifactId>
        </dependency>

        <!-- Spring Security -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>

        <!-- Spring Boot Actuator -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>

        <!-- Lombok（可选） -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- 测试依赖 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <version>2.2.5.RELEASE</version>
                <executions>
                    <execution>
                        <goals>
                            <goal>repackage</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

### 3.2 启动类

```java
package com.huabin.eureka.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Eureka服务端启动类
 * 
 * @author huabin
 * @date 2024-01-19
 */
@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }
}
```

### 3.3 Security配置类

```java
package com.huabin.eureka.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Spring Security配置类
 * 实现Eureka Server的HTTP Basic认证
 * 
 * @author huabin
 * @date 2024-01-19
 */
@Configuration
@EnableWebSecurity
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {

    /**
     * 配置HTTP安全规则
     */
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
            // 关闭CSRF保护
            // Eureka使用REST API，不需要CSRF保护
            .csrf().disable()
            
            // 配置请求授权规则
            .authorizeRequests()
                // 允许访问健康检查端点（不需要认证）
                .antMatchers("/actuator/**").permitAll()
                
                // 允许访问Eureka的静态资源（CSS、JS等）
                .antMatchers("/eureka/css/**", "/eureka/js/**", "/eureka/fonts/**").permitAll()
                
                // 其他所有请求都需要认证
                .anyRequest().authenticated()
            .and()
            
            // 启用HTTP Basic认证
            .httpBasic();
    }

    /**
     * 配置用户认证信息
     * 这里使用内存方式存储用户信息
     * 生产环境建议使用数据库或LDAP
     */
    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.inMemoryAuthentication()
            .passwordEncoder(passwordEncoder())
            
            // 管理员账号（拥有所有权限）
            .withUser("admin")
            .password(passwordEncoder().encode("admin123"))
            .roles("ADMIN")
            
            .and()
            
            // 普通服务账号（用于服务注册和发现）
            .withUser("eureka")
            .password(passwordEncoder().encode("eureka123"))
            .roles("USER");
    }

    /**
     * 密码编码器
     * 使用BCrypt加密算法
     * 
     * @return PasswordEncoder
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

### 3.4 配置文件（单机模式）

```yaml
# application.yml
server:
  port: 8761

spring:
  application:
    name: eureka-server
  
  # Spring Security配置
  security:
    user:
      # 默认用户名和密码（会被WebSecurityConfig覆盖）
      name: eureka
      password: eureka123

eureka:
  instance:
    hostname: localhost
    # 是否优先使用IP地址
    prefer-ip-address: false
    # 实例ID
    instance-id: ${spring.application.name}:${server.port}
    
  client:
    # 是否将自己注册到Eureka Server（单机模式设置为false）
    register-with-eureka: false
    # 是否从Eureka Server拉取服务列表（单机模式设置为false）
    fetch-registry: false
    # Eureka Server地址
    service-url:
      # 注意：这里需要带上用户名和密码
      defaultZone: http://eureka:eureka123@${eureka.instance.hostname}:${server.port}/eureka/
  
  server:
    # 是否启用自我保护模式
    # 开发环境可以关闭，生产环境建议开启
    enable-self-preservation: true
    # 自我保护模式的续约比例阈值（默认0.85）
    renewal-percent-threshold: 0.85
    # 清理无效节点的时间间隔（毫秒）
    eviction-interval-timer-in-ms: 60000
    # 响应缓存更新间隔（毫秒）
    response-cache-update-interval-ms: 30000
    # 响应缓存过期时间（秒）
    response-cache-auto-expiration-in-seconds: 180

# 日志配置
logging:
  level:
    root: INFO
    com.netflix.eureka: DEBUG
    com.netflix.discovery: DEBUG
    org.springframework.security: DEBUG
  pattern:
    console: '%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{50} - %msg%n'

# Actuator配置
management:
  endpoints:
    web:
      exposure:
        include: '*'
  endpoint:
    health:
      show-details: always
```

### 3.5 配置文件（集群模式）

#### application-peer1.yml（节点1）

```yaml
server:
  port: 8761

spring:
  application:
    name: eureka-server
  security:
    user:
      name: eureka
      password: eureka123

eureka:
  instance:
    hostname: peer1
    prefer-ip-address: false
    instance-id: ${spring.application.name}:${server.port}
    
  client:
    # 集群模式需要注册到其他节点
    register-with-eureka: true
    fetch-registry: true
    service-url:
      # 注册到peer2和peer3
      defaultZone: http://eureka:eureka123@peer2:8762/eureka/,http://eureka:eureka123@peer3:8763/eureka/
  
  server:
    enable-self-preservation: true
    renewal-percent-threshold: 0.85
    eviction-interval-timer-in-ms: 60000

logging:
  level:
    root: INFO
    com.netflix.eureka: DEBUG
    com.netflix.discovery: DEBUG
```

#### application-peer2.yml（节点2）

```yaml
server:
  port: 8762

spring:
  application:
    name: eureka-server
  security:
    user:
      name: eureka
      password: eureka123

eureka:
  instance:
    hostname: peer2
    prefer-ip-address: false
    instance-id: ${spring.application.name}:${server.port}
    
  client:
    register-with-eureka: true
    fetch-registry: true
    service-url:
      # 注册到peer1和peer3
      defaultZone: http://eureka:eureka123@peer1:8761/eureka/,http://eureka:eureka123@peer3:8763/eureka/
  
  server:
    enable-self-preservation: true
    renewal-percent-threshold: 0.85
    eviction-interval-timer-in-ms: 60000

logging:
  level:
    root: INFO
    com.netflix.eureka: DEBUG
    com.netflix.discovery: DEBUG
```

#### application-peer3.yml（节点3）

```yaml
server:
  port: 8763

spring:
  application:
    name: eureka-server
  security:
    user:
      name: eureka
      password: eureka123

eureka:
  instance:
    hostname: peer3
    prefer-ip-address: false
    instance-id: ${spring.application.name}:${server.port}
    
  client:
    register-with-eureka: true
    fetch-registry: true
    service-url:
      # 注册到peer1和peer2
      defaultZone: http://eureka:eureka123@peer1:8761/eureka/,http://eureka:eureka123@peer2:8762/eureka/
  
  server:
    enable-self-preservation: true
    renewal-percent-threshold: 0.85
    eviction-interval-timer-in-ms: 60000

logging:
  level:
    root: INFO
    com.netflix.eureka: DEBUG
    com.netflix.discovery: DEBUG
```

---

## 四、Eureka Client Producer实现

### 4.1 POM配置

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.huabin</groupId>
        <artifactId>springboot-eureka-learning</artifactId>
        <version>1.0.0</version>
    </parent>

    <artifactId>eureka-client-producer</artifactId>
    <name>eureka-client-producer</name>
    <description>Eureka客户端 - 服务提供者</description>

    <dependencies>
        <!-- Eureka Client -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>

        <!-- Spring Boot Web -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Spring Boot Actuator -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>

        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- 测试依赖 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <version>2.2.5.RELEASE</version>
                <executions>
                    <execution>
                        <goals>
                            <goal>repackage</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

### 4.2 启动类

```java
package com.huabin.eureka.producer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;

/**
 * 服务提供者启动类
 * 
 * @author huabin
 * @date 2024-01-19
 */
@SpringBootApplication
@EnableEurekaClient
public class ProducerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProducerApplication.class, args);
    }
}
```

### 4.3 Controller

```java
package com.huabin.eureka.producer.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Hello Controller
 * 提供简单的REST接口
 * 
 * @author huabin
 * @date 2024-01-19
 */
@Slf4j
@RestController
@RequestMapping("/hello")
public class HelloController {

    @Value("${server.port}")
    private String port;

    @Value("${spring.application.name}")
    private String applicationName;

    /**
     * Hello接口
     * 
     * @param name 名称
     * @return 问候语
     */
    @GetMapping("/{name}")
    public String hello(@PathVariable String name) {
        String message = String.format("Hello %s! 来自服务: %s, 端口: %s, 时间: %s",
                name,
                applicationName,
                port,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        
        log.info("收到请求，返回: {}", message);
        return message;
    }

    /**
     * 获取服务信息
     * 
     * @return 服务信息
     */
    @GetMapping("/info")
    public String info() {
        return String.format("服务名称: %s, 端口: %s", applicationName, port);
    }
}
```

### 4.4 配置文件

```yaml
# application.yml
server:
  port: 8001

spring:
  application:
    name: producer-service

eureka:
  client:
    # 是否将自己注册到Eureka Server
    register-with-eureka: true
    # 是否从Eureka Server拉取服务列表
    fetch-registry: true
    # Eureka Server地址（带认证信息）
    service-url:
      defaultZone: http://eureka:eureka123@localhost:8761/eureka/
    # 拉取服务列表的时间间隔（秒）
    registry-fetch-interval-seconds: 30
    
  instance:
    # 是否优先使用IP地址
    prefer-ip-address: true
    # 实例ID（格式：IP:应用名:端口）
    instance-id: ${spring.cloud.client.ip-address}:${spring.application.name}:${server.port}
    # 心跳间隔（秒）
    lease-renewal-interval-in-seconds: 30
    # 租约过期时间（秒）
    lease-expiration-duration-in-seconds: 90
    # 元数据
    metadata-map:
      zone: zone1
      version: 1.0.0

# 日志配置
logging:
  level:
    root: INFO
    com.huabin.eureka.producer: DEBUG
    com.netflix.discovery: DEBUG
  pattern:
    console: '%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{50} - %msg%n'

# Actuator配置
management:
  endpoints:
    web:
      exposure:
        include: '*'
  endpoint:
    health:
      show-details: always
```

---

## 五、Eureka Client Consumer实现

### 5.1 POM配置

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.huabin</groupId>
        <artifactId>springboot-eureka-learning</artifactId>
        <version>1.0.0</version>
    </parent>

    <artifactId>eureka-client-consumer</artifactId>
    <name>eureka-client-consumer</name>
    <description>Eureka客户端 - 服务消费者</description>

    <dependencies>
        <!-- Eureka Client -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>

        <!-- Spring Boot Web -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Ribbon（负载均衡） -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-ribbon</artifactId>
        </dependency>

        <!-- Spring Boot Actuator -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>

        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- 测试依赖 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <version>2.2.5.RELEASE</version>
                <executions>
                    <execution>
                        <goals>
                            <goal>repackage</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

### 5.2 启动类

```java
package com.huabin.eureka.consumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

/**
 * 服务消费者启动类
 * 
 * @author huabin
 * @date 2024-01-19
 */
@SpringBootApplication
@EnableEurekaClient
public class ConsumerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConsumerApplication.class, args);
    }

    /**
     * 配置RestTemplate
     * @LoadBalanced注解开启负载均衡功能
     * 
     * @return RestTemplate
     */
    @Bean
    @LoadBalanced
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
```

### 5.3 Controller

```java
package com.huabin.eureka.consumer.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * 消费者Controller
 * 调用生产者服务
 * 
 * @author huabin
 * @date 2024-01-19
 */
@Slf4j
@RestController
@RequestMapping("/consumer")
public class ConsumerController {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private DiscoveryClient discoveryClient;

    /**
     * 调用生产者服务
     * 使用服务名称调用，Ribbon会自动进行负载均衡
     * 
     * @param name 名称
     * @return 响应结果
     */
    @GetMapping("/hello/{name}")
    public String hello(@PathVariable String name) {
        // 使用服务名称调用（会自动负载均衡）
        String url = "http://producer-service/hello/" + name;
        
        log.info("调用生产者服务: {}", url);
        String result = restTemplate.getForObject(url, String.class);
        
        log.info("收到响应: {}", result);
        return "消费者调用结果: " + result;
    }

    /**
     * 获取服务信息
     * 
     * @return 服务信息
     */
    @GetMapping("/info")
    public String info() {
        // 使用服务名称调用
        String url = "http://producer-service/hello/info";
        
        log.info("调用生产者服务: {}", url);
        String result = restTemplate.getForObject(url, String.class);
        
        return result;
    }

    /**
     * 获取所有服务列表
     * 
     * @return 服务列表
     */
    @GetMapping("/services")
    public List<String> getServices() {
        List<String> services = discoveryClient.getServices();
        log.info("获取到的服务列表: {}", services);
        return services;
    }

    /**
     * 获取指定服务的所有实例
     * 
     * @param serviceName 服务名称
     * @return 实例列表
     */
    @GetMapping("/instances/{serviceName}")
    public List<ServiceInstance> getInstances(@PathVariable String serviceName) {
        List<ServiceInstance> instances = discoveryClient.getInstances(serviceName);
        log.info("服务 {} 的实例列表: {}", serviceName, instances);
        return instances;
    }
}
```

### 5.4 配置文件

```yaml
# application.yml
server:
  port: 9001

spring:
  application:
    name: consumer-service

eureka:
  client:
    # 是否将自己注册到Eureka Server
    register-with-eureka: true
    # 是否从Eureka Server拉取服务列表
    fetch-registry: true
    # Eureka Server地址（带认证信息）
    service-url:
      defaultZone: http://eureka:eureka123@localhost:8761/eureka/
    # 拉取服务列表的时间间隔（秒）
    registry-fetch-interval-seconds: 30
    
  instance:
    # 是否优先使用IP地址
    prefer-ip-address: true
    # 实例ID
    instance-id: ${spring.cloud.client.ip-address}:${spring.application.name}:${server.port}
    # 心跳间隔（秒）
    lease-renewal-interval-in-seconds: 30
    # 租约过期时间（秒）
    lease-expiration-duration-in-seconds: 90
    # 元数据
    metadata-map:
      zone: zone1
      version: 1.0.0

# Ribbon配置
producer-service:
  ribbon:
    # 负载均衡策略（轮询）
    NFLoadBalancerRuleClassName: com.netflix.loadbalancer.RoundRobinRule
    # 连接超时时间（毫秒）
    ConnectTimeout: 3000
    # 读取超时时间（毫秒）
    ReadTimeout: 3000
    # 最大重试次数
    MaxAutoRetries: 1
    # 切换实例的最大重试次数
    MaxAutoRetriesNextServer: 1

# 日志配置
logging:
  level:
    root: INFO
    com.huabin.eureka.consumer: DEBUG
    com.netflix.discovery: DEBUG
  pattern:
    console: '%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{50} - %msg%n'

# Actuator配置
management:
  endpoints:
    web:
      exposure:
        include: '*'
  endpoint:
    health:
      show-details: always
```

---

## 六、启动和测试

### 6.1 启动步骤

#### 1. 启动Eureka Server

```bash
# 进入eureka-server目录
cd eureka-server

# 编译打包
mvn clean package

# 启动（单机模式）
java -jar target/eureka-server-1.0.0.jar

# 或者使用Maven启动
mvn spring-boot:run
```

**验证**：
- 访问：http://localhost:8761
- 输入用户名：`eureka`，密码：`eureka123`
- 应该能看到Eureka控制台

#### 2. 启动Producer服务

```bash
# 进入eureka-client-producer目录
cd eureka-client-producer

# 编译打包
mvn clean package

# 启动
java -jar target/eureka-client-producer-1.0.0.jar

# 或者使用Maven启动
mvn spring-boot:run
```

**验证**：
- 刷新Eureka控制台（http://localhost:8761）
- 应该能看到`PRODUCER-SERVICE`已注册

#### 3. 启动Consumer服务

```bash
# 进入eureka-client-consumer目录
cd eureka-client-consumer

# 编译打包
mvn clean package

# 启动
java -jar target/eureka-client-consumer-1.0.0.jar

# 或者使用Maven启动
mvn spring-boot:run
```

**验证**：
- 刷新Eureka控制台
- 应该能看到`CONSUMER-SERVICE`已注册

### 6.2 功能测试

#### 1. 测试Producer服务

```bash
# 直接调用Producer
curl http://localhost:8001/hello/World

# 预期响应：
# Hello World! 来自服务: producer-service, 端口: 8001, 时间: 2024-01-19 10:30:00

# 获取服务信息
curl http://localhost:8001/hello/info

# 预期响应：
# 服务名称: producer-service, 端口: 8001
```

#### 2. 测试Consumer服务

```bash
# 通过Consumer调用Producer
curl http://localhost:9001/consumer/hello/World

# 预期响应：
# 消费者调用结果: Hello World! 来自服务: producer-service, 端口: 8001, 时间: 2024-01-19 10:30:00

# 获取所有服务列表
curl http://localhost:9001/consumer/services

# 预期响应：
# ["producer-service","consumer-service"]

# 获取Producer服务的所有实例
curl http://localhost:9001/consumer/instances/producer-service

# 预期响应：
# [{"host":"192.168.1.100","port":8001,"serviceId":"producer-service",...}]
```

#### 3. 测试负载均衡

```bash
# 启动第二个Producer实例（端口8002）
java -jar target/eureka-client-producer-1.0.0.jar --server.port=8002

# 多次调用Consumer，观察响应的端口号
curl http://localhost:9001/consumer/hello/World
# 第1次：端口8001
# 第2次：端口8002
# 第3次：端口8001
# 第4次：端口8002
# ...（轮询）
```

### 6.3 认证测试

#### 1. 测试无认证访问（应该失败）

```bash
# 尝试不带认证信息访问Eureka Server
curl http://localhost:8761/eureka/apps

# 预期响应：401 Unauthorized
```

#### 2. 测试带认证访问（应该成功）

```bash
# 带认证信息访问
curl -u eureka:eureka123 http://localhost:8761/eureka/apps

# 或者使用Authorization头
curl -H "Authorization: Basic ZXVyZWthOmV1cmVrYTEyMw==" http://localhost:8761/eureka/apps

# 预期响应：返回所有注册的服务列表（XML格式）
```

#### 3. 测试错误的认证信息（应该失败）

```bash
# 使用错误的密码
curl -u eureka:wrongpassword http://localhost:8761/eureka/apps

# 预期响应：401 Unauthorized
```

---

## 七、常见问题排查

### 7.1 服务无法注册

**问题现象**：
```
com.netflix.discovery.shared.transport.TransportException: 
Cannot execute request on any known server
```

**排查步骤**：

1. **检查Eureka Server是否启动**
   ```bash
   # 检查端口是否监听
   netstat -an | grep 8761
   
   # 或者
   lsof -i:8761
   ```

2. **检查认证信息是否正确**
   ```yaml
   # 确认配置文件中的用户名和密码
   eureka:
     client:
       service-url:
         defaultZone: http://eureka:eureka123@localhost:8761/eureka/
   ```

3. **检查网络连接**
   ```bash
   # 测试连接
   telnet localhost 8761
   
   # 或者
   curl -v http://localhost:8761
   ```

4. **查看日志**
   ```bash
   # 查看Eureka Server日志
   tail -f logs/spring.log
   
   # 查看Client日志
   tail -f logs/spring.log | grep "DiscoveryClient"
   ```

### 7.2 CSRF错误

**问题现象**：
```
403 Forbidden
Could not verify the provided CSRF token
```

**解决方案**：

确认Security配置中已关闭CSRF：
```java
@Override
protected void configure(HttpSecurity http) throws Exception {
    http.csrf().disable();  // 必须关闭
}
```

### 7.3 密码编码错误

**问题现象**：
```
java.lang.IllegalArgumentException: There is no PasswordEncoder mapped for the id "null"
```

**解决方案**：

确认配置了PasswordEncoder：
```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}

@Override
protected void configure(AuthenticationManagerBuilder auth) throws Exception {
    auth.inMemoryAuthentication()
        .passwordEncoder(passwordEncoder())  // 必须指定
        .withUser("eureka")
        .password(passwordEncoder().encode("eureka123"))
        .roles("USER");
}
```

### 7.4 服务调用失败

**问题现象**：
```
java.net.UnknownHostException: producer-service
```

**排查步骤**：

1. **确认服务已注册**
   - 访问Eureka控制台，检查服务是否在列表中

2. **确认服务名称正确**
   ```java
   // 服务名称必须与注册的名称一致
   String url = "http://producer-service/hello/World";  // 正确
   String url = "http://PRODUCER-SERVICE/hello/World";  // 也可以（不区分大小写）
   ```

3. **确认RestTemplate配置了@LoadBalanced**
   ```java
   @Bean
   @LoadBalanced  // 必须添加此注解
   public RestTemplate restTemplate() {
       return new RestTemplate();
   }
   ```

---

## 八、总结

### 8.1 核心要点

```
1. Eureka Server端口鉴权通过Spring Security实现
2. 使用HTTP Basic认证方式
3. 客户端连接时需要在URL中携带用户名和密码
4. URL格式：http://username:password@hostname:port/eureka/
5. 必须关闭CSRF保护
6. 必须配置PasswordEncoder
7. 生产环境建议配合HTTPS使用
```

### 8.2 配置清单

```yaml
# Eureka Server
spring:
  security:
    user:
      name: eureka
      password: eureka123

eureka:
  client:
    service-url:
      defaultZone: http://eureka:eureka123@localhost:8761/eureka/

# Eureka Client
eureka:
  client:
    service-url:
      defaultZone: http://eureka:eureka123@localhost:8761/eureka/
```

### 8.3 最佳实践

```
1. 使用强密码，定期更换
2. 不要将密码硬编码在代码中
3. 使用配置中心管理密码
4. 生产环境使用HTTPS
5. 配置访问控制，限制不同用户的权限
6. 记录审计日志
7. 定期检查安全漏洞
8. 使用防火墙限制访问来源
```

---

**下一篇**：[04-Eureka高可用集群部署.md](./04-Eureka高可用集群部署.md) - 详细介绍Eureka集群部署方案和配置
