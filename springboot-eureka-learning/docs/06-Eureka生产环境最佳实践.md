# 06-Eureka生产环境最佳实践

## 一、生产环境架构设计

### 1.1 高可用架构

#### 1.1.1 Eureka Server集群部署

```
推荐架构：三节点集群（最小高可用配置）

┌─────────────────────────────────────────────────────────┐
│                    Eureka Server集群                     │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │ Eureka-1     │  │ Eureka-2     │  │ Eureka-3     │  │
│  │ Zone: zone1  │  │ Zone: zone2  │  │ Zone: zone3  │  │
│  │ Port: 8761   │  │ Port: 8762   │  │ Port: 8763   │  │
│  └──────────────┘  └──────────────┘  └──────────────┘  │
│         ↕                 ↕                 ↕            │
│         └─────────────────┴─────────────────┘           │
│                  互相注册，数据同步                       │
│                                                          │
└─────────────────────────────────────────────────────────┘
                           ↑
                           │ 服务注册与发现
                           │
┌─────────────────────────────────────────────────────────┐
│                    微服务实例                            │
├─────────────────────────────────────────────────────────┤
│  Service-A-1  Service-A-2  Service-B-1  Service-B-2     │
└─────────────────────────────────────────────────────────┘
```

#### 1.1.2 Eureka Server配置

**节点1配置（eureka-server-1）**：

```yaml
server:
  port: 8761

spring:
  application:
    name: eureka-server

eureka:
  instance:
    hostname: eureka-server-1
    prefer-ip-address: false
    # 实例ID
    instance-id: ${spring.cloud.client.ip-address}:${server.port}
    # 心跳间隔
    lease-renewal-interval-in-seconds: 10
    # 租约过期时间
    lease-expiration-duration-in-seconds: 30
    # 元数据
    metadata-map:
      zone: zone1
      
  client:
    # Server自己也作为Client注册到其他节点
    register-with-eureka: true
    fetch-registry: true
    # 其他Eureka Server地址
    service-url:
      defaultZone: http://eureka-server-2:8762/eureka/,http://eureka-server-3:8763/eureka/
    # 拉取注册表间隔
    registry-fetch-interval-seconds: 10
    
  server:
    # 关键配置：生产环境必须开启自我保护
    enable-self-preservation: true
    # 自我保护阈值
    renewal-percent-threshold: 0.85
    # 剔除间隔
    eviction-interval-timer-in-ms: 10000
    # 响应缓存更新间隔
    response-cache-update-interval-ms: 3000
    # 响应缓存过期时间
    response-cache-auto-expiration-in-seconds: 180
    # 是否使用只读缓存
    use-read-only-response-cache: true
    # 增量信息缓存时间
    retention-time-in-m-s-in-delta-queue: 180000
    # 同步重试次数
    number-of-replication-retries: 5
    # 对等节点更新间隔
    peer-eureka-nodes-update-interval-ms: 600000
    # 对等节点连接超时
    peer-node-connect-timeout-ms: 2000
    # 对等节点读取超时
    peer-node-read-timeout-ms: 5000
    
  dashboard:
    # 是否启用控制台
    enabled: true
    
# 日志配置
logging:
  level:
    com.netflix.eureka: INFO
    com.netflix.discovery: INFO
  file:
    name: /var/log/eureka/eureka-server.log
    max-size: 100MB
    max-history: 30
```

**节点2和节点3配置类似，只需修改**：
- `server.port`
- `eureka.instance.hostname`
- `eureka.instance.metadata-map.zone`
- `eureka.client.service-url.defaultZone`（指向其他节点）

### 1.2 多机房部署

#### 1.2.1 跨机房架构

```
┌────────────────────────────────────────────────────────────┐
│                        机房A (Zone1)                        │
├────────────────────────────────────────────────────────────┤
│  Eureka-A1  Eureka-A2                                      │
│      ↓          ↓                                          │
│  Service-A1  Service-A2  Service-B1                        │
└────────────────────────────────────────────────────────────┘
                    ↕ (跨机房同步)
┌────────────────────────────────────────────────────────────┐
│                        机房B (Zone2)                        │
├────────────────────────────────────────────────────────────┤
│  Eureka-B1  Eureka-B2                                      │
│      ↓          ↓                                          │
│  Service-A3  Service-A4  Service-B2                        │
└────────────────────────────────────────────────────────────┘

优先级：
1. 优先访问同Zone的服务实例（就近原则）
2. 同Zone不可用时，访问其他Zone
3. 所有Zone都不可用时，触发降级
```

#### 1.2.2 Zone配置

**Eureka Server配置**：

```yaml
eureka:
  instance:
    metadata-map:
      zone: zone1  # 标识所属Zone
      
  client:
    # 优先注册到同Zone的Server
    prefer-same-zone-eureka: true
    # 按Zone配置Server地址
    service-url:
      zone1: http://eureka-a1:8761/eureka/,http://eureka-a2:8762/eureka/
      zone2: http://eureka-b1:8761/eureka/,http://eureka-b2:8762/eureka/
    # 可用Zone列表
    availability-zones:
      region1: zone1,zone2
    region: region1
```

**Eureka Client配置**：

```yaml
eureka:
  instance:
    metadata-map:
      zone: zone1  # 标识实例所属Zone
      
  client:
    service-url:
      defaultZone: http://eureka-a1:8761/eureka/,http://eureka-a2:8762/eureka/
    # 启用Zone亲和性
    prefer-same-zone-eureka: true
    availability-zones:
      region1: zone1,zone2
    region: region1
    
# Ribbon配置
ribbon:
  # 使用Zone避让策略
  NFLoadBalancerRuleClassName: com.netflix.loadbalancer.ZoneAvoidanceRule
  # 启用Zone亲和性
  EnableZoneAffinity: true
  # 不启用Zone独占
  EnableZoneExclusivity: false
```

## 二、故障转移优化配置

### 2.1 快速故障检测配置

#### 2.1.1 Eureka Server配置

```yaml
eureka:
  server:
    # 剔除间隔：10秒（默认60秒）
    eviction-interval-timer-in-ms: 10000
    # 响应缓存更新：3秒（默认30秒）
    response-cache-update-interval-ms: 3000
    # 自我保护：生产环境必须开启
    enable-self-preservation: true
    # 自我保护阈值：85%
    renewal-percent-threshold: 0.85
```

#### 2.1.2 Eureka Client配置

```yaml
eureka:
  instance:
    # 心跳间隔：10秒（默认30秒）
    lease-renewal-interval-in-seconds: 10
    # 租约过期：30秒（默认90秒）
    lease-expiration-duration-in-seconds: 30
    # 启用健康检查
    health-check-url-path: /actuator/health
    status-page-url-path: /actuator/info
    
  client:
    # 拉取注册表间隔：10秒（默认30秒）
    registry-fetch-interval-seconds: 10
    # 启用健康检查
    healthcheck:
      enabled: true
    # 初始化时立即拉取注册表
    initial-instance-info-replication-interval-seconds: 10
```

**⚠️ 注意**：缩短时间间隔会增加网络开销和Server压力，需要根据实际情况权衡。

### 2.2 重试与超时配置

#### 2.2.1 Ribbon配置

```yaml
ribbon:
  # 连接超时：1秒
  ConnectTimeout: 1000
  # 读取超时：3秒
  ReadTimeout: 3000
  # 同一实例重试次数：0（不重试）
  MaxAutoRetries: 0
  # 切换实例重试次数：2
  MaxAutoRetriesNextServer: 2
  # 只对GET请求重试
  OkToRetryOnAllOperations: false
  
  # 服务列表刷新间隔：10秒
  ServerListRefreshInterval: 10000
  
  # 连接池配置
  MaxConnectionsPerHost: 200
  MaxTotalConnections: 500
  
  # Ping配置
  NFLoadBalancerPingClassName: com.netflix.loadbalancer.PingUrl
  NFLoadBalancerPingInterval: 5
```

**总超时计算**：
```
总超时 = (ConnectTimeout + ReadTimeout) × (MaxAutoRetries + 1) × (MaxAutoRetriesNextServer + 1)
      = (1000 + 3000) × (0 + 1) × (2 + 1)
      = 4000 × 1 × 3
      = 12秒
```

#### 2.2.2 Hystrix配置

```yaml
hystrix:
  command:
    default:
      execution:
        timeout:
          enabled: true
        isolation:
          thread:
            # Hystrix超时必须大于Ribbon总超时
            timeoutInMilliseconds: 15000
      circuitBreaker:
        # 启用熔断器
        enabled: true
        # 触发熔断的最小请求数
        requestVolumeThreshold: 20
        # 错误率阈值
        errorThresholdPercentage: 50
        # 熔断后多久尝试恢复
        sleepWindowInMilliseconds: 5000
        # 强制打开熔断器（调试用）
        forceOpen: false
        # 强制关闭熔断器（调试用）
        forceClosed: false
      metrics:
        rollingStats:
          # 统计窗口时间
          timeInMilliseconds: 10000
          # 统计桶数量
          numBuckets: 10
  threadpool:
    default:
      # 核心线程数
      coreSize: 10
      # 最大线程数
      maximumSize: 20
      # 允许最大线程数生效
      allowMaximumSizeToDivergeFromCoreSize: true
      # 队列大小
      maxQueueSize: 200
```

### 2.3 健康检查增强

#### 2.3.1 自定义健康检查

```java
@Component
public class CustomHealthCheckHandler implements HealthCheckHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(CustomHealthCheckHandler.class);
    
    @Autowired
    private DataSource dataSource;
    
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    
    @Override
    public InstanceInfo.InstanceStatus getStatus(InstanceInfo.InstanceStatus currentStatus) {
        // 检查数据库连接
        if (!checkDatabase()) {
            logger.error("Database health check failed");
            return InstanceInfo.InstanceStatus.DOWN;
        }
        
        // 检查Redis连接
        if (!checkRedis()) {
            logger.error("Redis health check failed");
            return InstanceInfo.InstanceStatus.DOWN;
        }
        
        // 检查磁盘空间
        if (!checkDiskSpace()) {
            logger.warn("Disk space is low");
            return InstanceInfo.InstanceStatus.OUT_OF_SERVICE;
        }
        
        // 检查内存使用
        if (!checkMemory()) {
            logger.warn("Memory usage is high");
            return InstanceInfo.InstanceStatus.OUT_OF_SERVICE;
        }
        
        return InstanceInfo.InstanceStatus.UP;
    }
    
    private boolean checkDatabase() {
        try (Connection conn = dataSource.getConnection()) {
            return conn.isValid(3);
        } catch (Exception e) {
            logger.error("Database check failed", e);
            return false;
        }
    }
    
    private boolean checkRedis() {
        try {
            redisTemplate.opsForValue().get("health_check");
            return true;
        } catch (Exception e) {
            logger.error("Redis check failed", e);
            return false;
        }
    }
    
    private boolean checkDiskSpace() {
        File root = new File("/");
        long usableSpace = root.getUsableSpace();
        long totalSpace = root.getTotalSpace();
        double usagePercent = (1 - (double) usableSpace / totalSpace) * 100;
        return usagePercent < 90;  // 磁盘使用率低于90%
    }
    
    private boolean checkMemory() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        double usagePercent = (double) usedMemory / maxMemory * 100;
        return usagePercent < 85;  // 内存使用率低于85%
    }
}
```

#### 2.3.2 注册健康检查

```java
@Configuration
public class EurekaClientConfig {
    
    @Bean
    public DiscoveryClient.DiscoveryClientOptionalArgs discoveryClientOptionalArgs(
            CustomHealthCheckHandler healthCheckHandler) {
        DiscoveryClient.DiscoveryClientOptionalArgs args = 
            new DiscoveryClient.DiscoveryClientOptionalArgs();
        args.setHealthCheckHandler(healthCheckHandler);
        return args;
    }
}
```

#### 2.3.3 Spring Boot Actuator集成

```yaml
# 依赖
# <dependency>
#     <groupId>org.springframework.boot</groupId>
#     <artifactId>spring-boot-starter-actuator</artifactId>
# </dependency>

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: always
  health:
    defaults:
      enabled: true
    db:
      enabled: true
    redis:
      enabled: true
    diskspace:
      enabled: true
      
eureka:
  client:
    healthcheck:
      enabled: true
```

## 三、监控与告警

### 3.1 Prometheus监控集成

#### 3.1.1 添加依赖

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

#### 3.1.2 配置Actuator

```yaml
management:
  endpoints:
    web:
      exposure:
        include: prometheus,health,info,metrics
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      application: ${spring.application.name}
      instance: ${eureka.instance.instance-id}
```

#### 3.1.3 自定义监控指标

```java
@Component
public class EurekaMonitoringMetrics {
    
    private final MeterRegistry meterRegistry;
    private final EurekaClient eurekaClient;
    
    public EurekaMonitoringMetrics(MeterRegistry meterRegistry, EurekaClient eurekaClient) {
        this.meterRegistry = meterRegistry;
        this.eurekaClient = eurekaClient;
        initMetrics();
    }
    
    private void initMetrics() {
        // 监控注册实例数
        Gauge.builder("eureka.registered.instances", this, 
            metrics -> getRegisteredInstancesCount())
            .description("Number of registered instances")
            .register(meterRegistry);
        
        // 监控可用实例数
        Gauge.builder("eureka.available.instances", this,
            metrics -> getAvailableInstancesCount())
            .description("Number of available instances")
            .register(meterRegistry);
    }
    
    private int getRegisteredInstancesCount() {
        Applications applications = eurekaClient.getApplications();
        return applications.getRegisteredApplications().stream()
            .mapToInt(app -> app.getInstances().size())
            .sum();
    }
    
    private int getAvailableInstancesCount() {
        Applications applications = eurekaClient.getApplications();
        return applications.getRegisteredApplications().stream()
            .flatMap(app -> app.getInstances().stream())
            .filter(instance -> instance.getStatus() == InstanceInfo.InstanceStatus.UP)
            .mapToInt(instance -> 1)
            .sum();
    }
    
    /**
     * 记录心跳失败
     */
    public void recordHeartbeatFailure(String serviceName) {
        meterRegistry.counter("eureka.heartbeat.failure",
            "service", serviceName).increment();
    }
    
    /**
     * 记录服务调用
     */
    public void recordServiceCall(String serviceName, String instanceId, 
                                   boolean success, long duration) {
        meterRegistry.timer("eureka.service.call",
            "service", serviceName,
            "instance", instanceId,
            "success", String.valueOf(success))
            .record(duration, TimeUnit.MILLISECONDS);
    }
    
    /**
     * 记录重试次数
     */
    public void recordRetry(String serviceName, int retryCount) {
        meterRegistry.counter("eureka.retry.count",
            "service", serviceName,
            "count", String.valueOf(retryCount))
            .increment();
    }
}
```

### 3.2 日志配置

#### 3.2.1 Logback配置

```xml
<!-- logback-spring.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <!-- 日志格式 -->
    <property name="LOG_PATTERN" 
              value="%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{50} - %msg%n"/>
    
    <!-- 控制台输出 -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>${LOG_PATTERN}</pattern>
        </encoder>
    </appender>
    
    <!-- 文件输出 -->
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>/var/log/eureka/application.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>/var/log/eureka/application.%d{yyyy-MM-dd}.%i.log</fileNamePattern>
            <timeBasedFileNamingAndTriggeringPolicy 
                class="ch.qos.logback.core.rolling.SizeAndTimeBasedFNATP">
                <maxFileSize>100MB</maxFileSize>
            </timeBasedFileNamingAndTriggeringPolicy>
            <maxHistory>30</maxHistory>
        </rollingPolicy>
        <encoder>
            <pattern>${LOG_PATTERN}</pattern>
        </encoder>
    </appender>
    
    <!-- Eureka专用日志 -->
    <appender name="EUREKA_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>/var/log/eureka/eureka.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>/var/log/eureka/eureka.%d{yyyy-MM-dd}.log</fileNamePattern>
            <maxHistory>30</maxHistory>
        </rollingPolicy>
        <encoder>
            <pattern>${LOG_PATTERN}</pattern>
        </encoder>
    </appender>
    
    <!-- 日志级别 -->
    <logger name="com.netflix.eureka" level="INFO" additivity="false">
        <appender-ref ref="EUREKA_FILE"/>
    </logger>
    
    <logger name="com.netflix.discovery" level="INFO" additivity="false">
        <appender-ref ref="EUREKA_FILE"/>
    </logger>
    
    <logger name="com.netflix.loadbalancer" level="DEBUG" additivity="false">
        <appender-ref ref="EUREKA_FILE"/>
    </logger>
    
    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="FILE"/>
    </root>
</configuration>
```

### 3.3 告警规则

#### 3.3.1 Prometheus告警规则

```yaml
# eureka-alerts.yml
groups:
  - name: eureka_alerts
    interval: 30s
    rules:
      # 实例下线告警
      - alert: EurekaInstanceDown
        expr: eureka_available_instances < eureka_registered_instances
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Eureka实例下线"
          description: "服务 {{ $labels.application }} 有实例下线"
      
      # 心跳失败告警
      - alert: EurekaHeartbeatFailure
        expr: rate(eureka_heartbeat_failure_total[5m]) > 0.1
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: "Eureka心跳失败率过高"
          description: "服务 {{ $labels.service }} 心跳失败率: {{ $value }}"
      
      # 重试次数告警
      - alert: EurekaHighRetryRate
        expr: rate(eureka_retry_count_total[5m]) > 10
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: "Eureka重试次数过多"
          description: "服务 {{ $labels.service }} 重试率: {{ $value }}/s"
      
      # 服务调用失败率告警
      - alert: EurekaServiceCallFailure
        expr: |
          sum(rate(eureka_service_call_seconds_count{success="false"}[5m])) by (service)
          /
          sum(rate(eureka_service_call_seconds_count[5m])) by (service)
          > 0.1
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "服务调用失败率过高"
          description: "服务 {{ $labels.service }} 失败率: {{ $value | humanizePercentage }}"
      
      # 响应时间告警
      - alert: EurekaHighResponseTime
        expr: |
          histogram_quantile(0.95, 
            sum(rate(eureka_service_call_seconds_bucket[5m])) by (service, le)
          ) > 3
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "服务响应时间过长"
          description: "服务 {{ $labels.service }} P95响应时间: {{ $value }}s"
```

## 四、安全加固

### 4.1 启用HTTPS

#### 4.1.1 生成证书

```bash
# 生成自签名证书（生产环境应使用CA签发的证书）
keytool -genkeypair -alias eureka-server \
  -keyalg RSA -keysize 2048 \
  -storetype PKCS12 \
  -keystore eureka-server.p12 \
  -validity 3650 \
  -storepass changeit \
  -dname "CN=eureka-server,OU=IT,O=Company,L=City,ST=State,C=CN"
```

#### 4.1.2 配置HTTPS

```yaml
server:
  port: 8443
  ssl:
    enabled: true
    key-store: classpath:eureka-server.p12
    key-store-password: changeit
    key-store-type: PKCS12
    key-alias: eureka-server
    
eureka:
  instance:
    secure-port-enabled: true
    secure-port: ${server.port}
    non-secure-port-enabled: false
    status-page-url: https://${eureka.instance.hostname}:${server.port}/actuator/info
    health-check-url: https://${eureka.instance.hostname}:${server.port}/actuator/health
    home-page-url: https://${eureka.instance.hostname}:${server.port}/
    
  client:
    service-url:
      defaultZone: https://eureka-server-1:8443/eureka/,https://eureka-server-2:8443/eureka/
```

### 4.2 启用认证

#### 4.2.1 添加依赖

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
```

#### 4.2.2 配置认证

```yaml
spring:
  security:
    user:
      name: admin
      password: ${EUREKA_PASSWORD:admin123}
      
eureka:
  client:
    service-url:
      defaultZone: http://admin:admin123@eureka-server-1:8761/eureka/
```

#### 4.2.3 Security配置

```java
@Configuration
@EnableWebSecurity
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {
    
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.csrf().disable()
            .authorizeRequests()
            .antMatchers("/actuator/health").permitAll()
            .anyRequest().authenticated()
            .and()
            .httpBasic();
    }
}
```

### 4.3 网络隔离

```yaml
# 只允许内网访问
eureka:
  instance:
    # 使用内网IP
    prefer-ip-address: true
    ip-address: 192.168.1.100
    
# 配置防火墙规则（示例）
# iptables -A INPUT -p tcp --dport 8761 -s 192.168.1.0/24 -j ACCEPT
# iptables -A INPUT -p tcp --dport 8761 -j DROP
```

## 五、性能优化

### 5.1 JVM参数优化

```bash
# 启动脚本
java -Xms2g -Xmx2g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=/var/log/eureka/heapdump.hprof \
  -XX:+PrintGCDetails \
  -XX:+PrintGCDateStamps \
  -Xloggc:/var/log/eureka/gc.log \
  -XX:+UseGCLogFileRotation \
  -XX:NumberOfGCLogFiles=10 \
  -XX:GCLogFileSize=100M \
  -Dserver.port=8761 \
  -jar eureka-server.jar
```

### 5.2 连接池优化

```yaml
# Tomcat连接池
server:
  tomcat:
    threads:
      max: 200
      min-spare: 10
    accept-count: 100
    max-connections: 10000
    connection-timeout: 20000
```

### 5.3 缓存优化

```yaml
eureka:
  server:
    # 使用只读缓存提高性能
    use-read-only-response-cache: true
    # 缓存更新间隔
    response-cache-update-interval-ms: 3000
    # 缓存过期时间
    response-cache-auto-expiration-in-seconds: 180
```

## 六、容灾与备份

### 6.1 数据备份

```java
@Component
@Scheduled(cron = "0 0 2 * * ?")  // 每天凌晨2点执行
public class EurekaBackupTask {
    
    @Autowired
    private EurekaClient eurekaClient;
    
    public void backupRegistry() {
        Applications applications = eurekaClient.getApplications();
        
        // 序列化为JSON
        ObjectMapper mapper = new ObjectMapper();
        try {
            String json = mapper.writeValueAsString(applications);
            
            // 保存到文件
            String filename = String.format("eureka-backup-%s.json", 
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
            Files.write(Paths.get("/var/backup/eureka", filename), 
                json.getBytes(StandardCharsets.UTF_8));
            
            // 清理旧备份（保留30天）
            cleanOldBackups();
        } catch (Exception e) {
            logger.error("Backup failed", e);
        }
    }
    
    private void cleanOldBackups() throws IOException {
        Path backupDir = Paths.get("/var/backup/eureka");
        long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30);
        
        Files.list(backupDir)
            .filter(path -> {
                try {
                    return Files.getLastModifiedTime(path).toMillis() < cutoff;
                } catch (IOException e) {
                    return false;
                }
            })
            .forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    logger.error("Failed to delete old backup: " + path, e);
                }
            });
    }
}
```

### 6.2 灾难恢复

```yaml
# 配置多个Eureka Server地址，确保高可用
eureka:
  client:
    service-url:
      defaultZone: |
        http://eureka-1:8761/eureka/,
        http://eureka-2:8762/eureka/,
        http://eureka-3:8763/eureka/
    # 启用备份注册表
    backup-registry-impl: com.netflix.discovery.BackupRegistry
```

## 七、生产环境检查清单

### 7.1 部署前检查

- [ ] Eureka Server至少3个节点
- [ ] 配置了HTTPS和认证
- [ ] 开启了自我保护机制
- [ ] 配置了合理的心跳和剔除时间
- [ ] 配置了健康检查
- [ ] 配置了监控和告警
- [ ] 配置了日志收集
- [ ] 进行了压力测试
- [ ] 准备了应急预案

### 7.2 运行时监控

- [ ] 监控注册实例数
- [ ] 监控心跳成功率
- [ ] 监控服务调用成功率
- [ ] 监控响应时间
- [ ] 监控JVM内存和GC
- [ ] 监控网络流量
- [ ] 定期检查日志

### 7.3 定期维护

- [ ] 定期备份注册表数据
- [ ] 定期清理日志文件
- [ ] 定期更新证书
- [ ] 定期演练故障恢复
- [ ] 定期review配置参数

## 八、总结

### 8.1 核心配置对比

| 配置项 | 开发环境 | 测试环境 | 生产环境 |
|--------|---------|---------|---------|
| Eureka Server节点数 | 1 | 2 | ≥3 |
| 心跳间隔 | 30s | 15s | 10s |
| 租约过期 | 90s | 45s | 30s |
| 剔除间隔 | 60s | 30s | 10s |
| 缓存更新 | 30s | 10s | 3s |
| 拉取间隔 | 30s | 15s | 10s |
| 自我保护 | 关闭 | 开启 | 开启 |
| HTTPS | 否 | 可选 | 是 |
| 认证 | 否 | 可选 | 是 |

### 8.2 最佳实践总结

1. **高可用**：至少3节点集群，跨机房部署
2. **快速故障检测**：适度缩短心跳和剔除时间
3. **健康检查**：自定义健康检查，检查依赖服务
4. **负载均衡**：使用ZoneAvoidanceRule，配置合理重试
5. **监控告警**：集成Prometheus，配置关键指标告警
6. **安全加固**：启用HTTPS和认证，网络隔离
7. **性能优化**：优化JVM参数，调整连接池和缓存
8. **容灾备份**：定期备份，准备应急预案

---

**相关文档**：
- [04-Eureka故障转移机制深度剖析.md](./04-Eureka故障转移机制深度剖析.md)
- [05-Eureka负载均衡策略详解.md](./05-Eureka负载均衡策略详解.md)
