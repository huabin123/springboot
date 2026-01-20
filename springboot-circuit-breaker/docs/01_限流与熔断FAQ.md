# 限流与熔断 FAQ

## 目录
- [基础概念](#基础概念)
- [核心区别](#核心区别)
- [实战应用](#实战应用)
- [常见问题](#常见问题)

---

## 基础概念

### 1. 什么是限流？

**限流（Rate Limiting）** 就像景区的入口闸机，控制单位时间内能进入的游客数量。

**通俗理解：**
- 你开了一家餐厅，只有10张桌子
- 限流就是在门口控制，同时最多只让10桌客人进来
- 超过的客人要么排队等待，要么直接拒绝

**技术角度：**
- 控制请求的速率，防止系统过载
- 保护系统资源不被耗尽
- 是一种**预防性**措施

**常见算法：**
```
1. 固定窗口：每秒最多100个请求
2. 滑动窗口：更平滑的限流
3. 令牌桶：允许一定程度的突发流量
4. 漏桶：严格控制流出速率
```

---

### 2. 什么是熔断？

**熔断（Circuit Breaker）** 就像家里的保险丝，当电流过大时自动断电保护电器。

**通俗理解：**
- 你家空调坏了，一直漏电
- 如果不断电，可能引发火灾
- 熔断器检测到异常后，直接切断电源
- 过一段时间再尝试恢复

**技术角度：**
- 当下游服务故障时，快速失败，避免级联故障
- 给故障服务恢复的时间
- 是一种**保护性**措施

**三种状态：**
```
1. 关闭状态（Closed）：正常工作，请求正常通过
2. 打开状态（Open）：熔断触发，直接返回失败
3. 半开状态（Half-Open）：尝试恢复，放少量请求测试
```

---

### 3. 什么是降级？

**降级（Degradation）** 是熔断后的兜底方案。

**通俗理解：**
- 你去餐厅吃饭，想点龙虾
- 服务员说："龙虾卖完了，给您来份大虾可以吗？"
- 这就是降级：提供备选方案

**技术角度：**
```java
// 正常情况：调用远程服务获取用户详情
UserDetail detail = remoteService.getUserDetail(userId);

// 降级情况：返回缓存或默认值
UserDetail detail = cache.get(userId); // 或返回默认头像、昵称
```

---

## 核心区别

### 4. 限流和熔断有什么本质区别？

| 维度 | 限流 | 熔断 |
|------|------|------|
| **触发时机** | 流量超过阈值 | 错误率/响应时间超过阈值 |
| **保护对象** | 保护自己 | 保护自己和下游 |
| **作用位置** | 入口处 | 调用链路上 |
| **处理方式** | 拒绝/排队 | 快速失败/降级 |
| **恢复机制** | 流量降低即可 | 需要探测下游是否恢复 |

**形象比喻：**

```
限流 = 景区门口的限流措施
- 今天最多接待1万人
- 超过了就不让进
- 保护景区不被踩坏

熔断 = 景区内某个景点的临时关闭
- 玻璃栈道发现裂缝（故障）
- 立即封闭该景点（熔断）
- 游客改去其他景点（降级）
- 修好后重新开放（恢复）
```

---

### 5. 为什么需要同时使用限流和熔断？

它们解决的是不同层面的问题：

**限流解决：** "我能承受多少压力"
```
场景：秒杀活动
- 系统最多支持1000 QPS
- 超过就会崩溃
- 所以在入口限流，只放1000 QPS进来
```

**熔断解决：** "依赖的服务挂了怎么办"
```
场景：订单服务依赖库存服务
- 库存服务突然响应很慢（3秒才返回）
- 如果不熔断，订单服务的线程会被耗尽
- 熔断后快速失败，保护订单服务
```

**单独使用的问题：**
```
只用限流：
✓ 能防止自己被压垮
✗ 无法应对下游故障
✗ 可能被慢调用拖死

只用熔断：
✓ 能应对下游故障
✗ 无法防止流量过大
✗ 可能被瞬时高并发打垮
```

---

## 实战应用

### 6. 在高并发场景中怎么搭配使用？

#### 场景一：电商秒杀系统

```
用户请求 → [限流] → [业务逻辑] → [熔断] → 下游服务
```

**分层防护：**

```java
// 第1层：网关层限流（保护整个系统）
@RateLimiter(qps = 10000)
public class GatewayController {
    // 全局限流：每秒最多1万请求
}

// 第2层：接口层限流（保护单个接口）
@RateLimiter(qps = 1000)
public class OrderController {
    
    @PostMapping("/seckill")
    public Result seckill(Long productId) {
        // 接口限流：秒杀接口每秒最多1000请求
        
        // 第3层：调用下游时熔断（保护调用链路）
        return orderService.createOrder(productId);
    }
}

// 第3层：服务层熔断
@Service
public class OrderService {
    
    @HystrixCommand(fallbackMethod = "createOrderFallback")
    public Result createOrder(Long productId) {
        // 调用库存服务
        Stock stock = stockService.deduct(productId);
        
        // 调用支付服务
        Payment payment = paymentService.create(order);
        
        return Result.success(order);
    }
    
    // 降级方法
    public Result createOrderFallback(Long productId) {
        return Result.fail("系统繁忙，请稍后再试");
    }
}
```

---

#### 场景二：微服务调用链

```
API网关 → 订单服务 → 库存服务
                  ↓
              支付服务
```

**配置策略：**

```yaml
# 1. 网关层：全局限流
gateway:
  rate-limiter:
    qps: 10000  # 总流量控制

# 2. 订单服务：接口限流 + 熔断
order-service:
  rate-limiter:
    qps: 5000   # 订单服务限流
  
  hystrix:
    # 调用库存服务的熔断配置
    stock-service:
      timeout: 1000ms              # 超时时间
      error-threshold: 50%         # 错误率超过50%熔断
      request-volume: 20           # 10秒内至少20个请求
      sleep-window: 5000ms         # 熔断后5秒尝试恢复
    
    # 调用支付服务的熔断配置
    payment-service:
      timeout: 2000ms
      error-threshold: 50%
      request-volume: 20
      sleep-window: 5000ms
```

---

### 7. 具体的搭配策略是什么？

#### 策略一：漏斗模型（推荐）

```
                    10万 QPS
                       ↓
            ┌──────────────────────┐
            │   网关限流: 1万 QPS   │  ← 第1道防线
            └──────────────────────┘
                       ↓
            ┌──────────────────────┐
            │  接口限流: 1000 QPS   │  ← 第2道防线
            └──────────────────────┘
                       ↓
            ┌──────────────────────┐
            │   熔断保护: 快速失败   │  ← 第3道防线
            └──────────────────────┘
                       ↓
                   下游服务
```

**配置原则：**
```
1. 限流阈值：从外到内逐层递减
   - 网关 > 服务 > 接口 > 方法

2. 超时时间：从内到外逐层递增
   - 方法(500ms) < 接口(1s) < 服务(2s) < 网关(3s)

3. 熔断策略：根据依赖重要性区分
   - 核心依赖：快速熔断，快速恢复
   - 非核心依赖：容忍度更高
```

---

#### 策略二：差异化配置

```java
public class RateLimitStrategy {
    
    // 1. 根据用户等级差异化限流
    public boolean allowRequest(User user) {
        if (user.isVip()) {
            return vipLimiter.tryAcquire();  // VIP: 100 QPS
        } else {
            return normalLimiter.tryAcquire(); // 普通: 10 QPS
        }
    }
    
    // 2. 根据接口重要性差异化配置
    @RateLimiter(qps = 1000)  // 核心接口：严格限流
    public Result createOrder() { }
    
    @RateLimiter(qps = 5000)  // 查询接口：宽松限流
    public Result queryOrder() { }
    
    // 3. 根据依赖重要性差异化熔断
    @HystrixCommand(
        fallbackMethod = "stockFallback",
        commandProperties = {
            @HystrixProperty(name = "execution.isolation.thread.timeoutInMilliseconds", 
                           value = "500")  // 库存服务：快速失败
        }
    )
    public Stock getStock() { }
    
    @HystrixCommand(
        fallbackMethod = "recommendFallback",
        commandProperties = {
            @HystrixProperty(name = "execution.isolation.thread.timeoutInMilliseconds", 
                           value = "2000")  // 推荐服务：容忍度高
        }
    )
    public List<Product> getRecommend() { }
}
```

---

### 8. 实际案例：如何处理突发流量？

#### 案例：双11零点流量洪峰

**问题：**
- 零点瞬间流量从1000 QPS暴增到10万 QPS
- 如何保证系统不崩溃？

**解决方案：**

```java
// 1. 预热限流（Warm Up）
@RateLimiter(
    qps = 10000,
    warmUpPeriodSec = 60  // 60秒内从1000逐渐增加到10000
)
public Result seckill() {
    // 避免冷启动时被瞬间打垮
}

// 2. 令牌桶算法（允许突发）
RateLimiter limiter = RateLimiter.create(1000, 10, TimeUnit.SECONDS);
// 平均1000 QPS，但允许10秒内的突发流量

// 3. 分级降级
public Result seckill(Long productId) {
    try {
        // 尝试完整流程
        return fullProcess(productId);
    } catch (Exception e) {
        // 第1级降级：关闭推荐服务
        return processWithoutRecommend(productId);
    } catch (Exception e) {
        // 第2级降级：只返回基本信息
        return basicInfo(productId);
    } catch (Exception e) {
        // 第3级降级：返回静态页面
        return staticPage();
    }
}

// 4. 动态调整
@Scheduled(fixedRate = 5000)  // 每5秒调整一次
public void adjustRateLimit() {
    int currentQps = monitor.getCurrentQps();
    int cpuUsage = monitor.getCpuUsage();
    
    if (cpuUsage > 80) {
        // CPU过高，降低限流阈值
        rateLimiter.setQps(currentQps * 0.8);
    } else if (cpuUsage < 50) {
        // CPU充足，提高限流阈值
        rateLimiter.setQps(currentQps * 1.2);
    }
}
```

---

## 常见问题

### 9. 限流被触发后，请求怎么处理？

**三种处理方式：**

```java
// 方式1：直接拒绝（适合非核心业务）
if (!rateLimiter.tryAcquire()) {
    return Result.fail("系统繁忙，请稍后再试");
}

// 方式2：等待排队（适合核心业务）
if (!rateLimiter.tryAcquire(3, TimeUnit.SECONDS)) {
    return Result.fail("排队超时，请稍后再试");
}

// 方式3：降级处理（适合可降级业务）
if (!rateLimiter.tryAcquire()) {
    return getCachedResult();  // 返回缓存数据
}
```

**选择建议：**
```
直接拒绝：秒杀、抢购等场景
等待排队：订单提交、支付等核心场景
降级处理：推荐、广告等非核心场景
```

---

### 10. 熔断后如何恢复？

**Hystrix 的恢复机制：**

```
1. 熔断打开（Open）
   ↓
   等待 sleepWindow（如5秒）
   ↓
2. 进入半开状态（Half-Open）
   ↓
   放行1个请求测试
   ↓
   成功？ ──Yes──→ 关闭熔断（Closed）
   │
   No
   ↓
   继续熔断，再等5秒
```

**配置示例：**

```java
@HystrixCommand(
    commandProperties = {
        // 熔断条件
        @HystrixProperty(name = "circuitBreaker.errorThresholdPercentage", 
                       value = "50"),  // 错误率50%
        @HystrixProperty(name = "circuitBreaker.requestVolumeThreshold", 
                       value = "20"),  // 至少20个请求
        
        // 恢复配置
        @HystrixProperty(name = "circuitBreaker.sleepWindowInMilliseconds", 
                       value = "5000"),  // 5秒后尝试恢复
    }
)
public String callRemoteService() {
    return restTemplate.getForObject(url, String.class);
}
```

---

### 11. 单机限流和分布式限流有什么区别？

**单机限流：**
```java
// 使用 Guava RateLimiter
RateLimiter limiter = RateLimiter.create(1000);  // 每秒1000个

// 问题：3台机器，每台1000 QPS = 总共3000 QPS
// 无法精确控制集群总流量
```

**分布式限流：**
```java
// 使用 Redis + Lua 脚本
@RateLimiter(key = "api:order:create", qps = 1000)
public Result createOrder() {
    // 3台机器共享这1000 QPS
    // 可以精确控制集群总流量
}
```

**对比：**

| 特性 | 单机限流 | 分布式限流 |
|------|---------|-----------|
| 实现难度 | 简单 | 复杂 |
| 性能 | 高（本地内存） | 较低（网络调用） |
| 精确度 | 单机精确 | 集群精确 |
| 适用场景 | 单体应用 | 微服务集群 |

---

### 12. 如何监控限流和熔断？

**关键指标：**

```java
// 1. 限流指标
public class RateLimitMetrics {
    private long totalRequests;      // 总请求数
    private long passedRequests;     // 通过的请求数
    private long blockedRequests;    // 被限流的请求数
    
    public double getBlockRate() {
        return (double) blockedRequests / totalRequests;
    }
}

// 2. 熔断指标
public class CircuitBreakerMetrics {
    private String serviceName;      // 服务名称
    private String status;           // 熔断状态：OPEN/CLOSED/HALF_OPEN
    private long errorCount;         // 错误次数
    private double errorRate;        // 错误率
    private long lastOpenTime;       // 最后熔断时间
}

// 3. 监控告警
@Scheduled(fixedRate = 60000)
public void checkMetrics() {
    if (rateLimitMetrics.getBlockRate() > 0.5) {
        // 限流率超过50%，发送告警
        alertService.send("限流率过高，可能需要扩容");
    }
    
    if (circuitBreakerMetrics.getStatus() == "OPEN") {
        // 熔断器打开，发送告警
        alertService.send("服务熔断：" + serviceName);
    }
}
```

**可视化工具：**
```
1. Hystrix Dashboard：实时监控熔断状态
2. Grafana + Prometheus：监控限流指标
3. Sentinel Dashboard：阿里的流控监控平台
```

---

### 13. 限流和熔断会影响用户体验吗？

**会，但可以优化：**

#### 优化策略：

```java
// 1. 友好的错误提示
if (!rateLimiter.tryAcquire()) {
    return Result.fail(
        "当前访问人数过多，您的排队号是：1234",
        "预计等待时间：30秒"
    );
}

// 2. 降级而不是失败
@HystrixCommand(fallbackMethod = "getRecommendFromCache")
public List<Product> getRecommend() {
    return remoteService.getRecommend();
}

public List<Product> getRecommendFromCache() {
    // 返回缓存的推荐数据，而不是直接失败
    return cache.getRecommend();
}

// 3. 异步处理
@RateLimiter(qps = 100)
public Result submitOrder(Order order) {
    if (!rateLimiter.tryAcquire()) {
        // 不是直接拒绝，而是放入队列异步处理
        messageQueue.send(order);
        return Result.success("订单已提交，请稍后查看处理结果");
    }
    return processOrder(order);
}

// 4. 分级服务
public Result getProductDetail(Long productId, User user) {
    if (user.isVip()) {
        // VIP用户：完整数据 + 推荐
        return getFullDetail(productId);
    } else {
        // 普通用户：基础数据
        return getBasicDetail(productId);
    }
}
```

---

### 14. 什么时候该用限流，什么时候该用熔断？

**决策树：**

```
问题：如何保护系统？
│
├─ 是否担心流量过大？
│  └─ Yes → 使用限流
│      ├─ 单体应用 → Guava RateLimiter
│      └─ 微服务集群 → Redis + Lua / Sentinel
│
└─ 是否依赖外部服务？
   └─ Yes → 使用熔断
       ├─ 依赖稳定性差 → 快速熔断（低阈值）
       └─ 依赖稳定性好 → 慢速熔断（高阈值）
```

**实际场景：**

| 场景 | 限流 | 熔断 | 原因 |
|------|------|------|------|
| 秒杀活动 | ✓ | ✗ | 主要是流量大，不涉及外部依赖 |
| 调用支付接口 | ✗ | ✓ | 依赖第三方，需要熔断保护 |
| API网关 | ✓ | ✓ | 既要限流，也要熔断下游 |
| 数据库查询 | ✓ | ✗ | 控制并发数，防止连接池耗尽 |
| 调用微服务 | ✓ | ✓ | 既限制调用频率，也防止雪崩 |

---

## 总结

### 核心要点

1. **限流是预防，熔断是保护**
   - 限流：防止流量过大压垮系统
   - 熔断：防止故障扩散导致雪崩

2. **分层防护，纵深防御**
   ```
   网关限流 → 接口限流 → 方法限流 → 熔断保护
   ```

3. **差异化策略**
   - 核心业务：严格限流 + 快速熔断
   - 非核心业务：宽松限流 + 容忍度高

4. **用户体验优先**
   - 降级 > 排队 > 拒绝
   - 友好提示 > 冷冰冰的错误

5. **持续监控和调优**
   - 监控关键指标
   - 根据实际情况动态调整
   - 定期压测验证

---

## 推荐阅读

- [Hystrix 官方文档](https://github.com/Netflix/Hystrix/wiki)
- [Sentinel 官方文档](https://sentinelguard.io/)
- [《微服务架构设计模式》- Chris Richardson](https://microservices.io/patterns/reliability/circuit-breaker.html)
- [Martin Fowler - Circuit Breaker](https://martinfowler.com/bliki/CircuitBreaker.html)
