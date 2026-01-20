# Eureka安全认证机制

## 一、为什么需要安全认证？

### 1.1 安全风险

在生产环境中，如果Eureka Server不做任何安全防护，会面临以下风险：

```
1. 恶意服务注册
   - 任何人都可以向Eureka注册服务
   - 可能注册虚假服务，导致服务调用失败
   - 可能注册恶意服务，窃取敏感数据

2. 服务信息泄露
   - 任何人都可以访问Eureka控制台
   - 可以查看所有服务的IP、端口等信息
   - 暴露系统架构和部署信息

3. 服务下线攻击
   - 恶意下线正常服务
   - 导致服务不可用
   - 影响业务正常运行

4. 注册表污染
   - 注册大量虚假服务
   - 消耗Eureka Server资源
   - 影响正常服务的注册和发现
```

### 1.2 安全需求

```
1. 身份认证
   - 验证客户端身份
   - 只允许合法客户端访问

2. 访问控制
   - 控制不同客户端的权限
   - 限制敏感操作

3. 数据加密
   - 传输层加密（HTTPS）
   - 防止数据被窃听

4. 审计日志
   - 记录所有操作
   - 便于追踪和审计
```

---

## 二、Eureka支持的认证方式

### 2.1 HTTP Basic认证

**原理**：

- 客户端在HTTP请求头中携带用户名和密码
- 服务端验证用户名和密码
- 验证通过后允许访问

**优点**：

- 实现简单
- Spring Security原生支持
- 适合内网环境

**缺点**：

- 安全性较低（Base64编码，非加密）
- 建议配合HTTPS使用（但不是强制要求）
- 每次请求都需要携带认证信息

**认证流程**：

```
┌─────────────┐                                    ┌─────────────┐
│   Client    │                                    │   Server    │
└──────┬──────┘                                    └──────┬──────┘
       │                                                  │
       │  1. 发送请求（不带认证信息）                      │
       │     GET /eureka/apps                            │
       ├────────────────────────────────────────────────▶│
       │                                                  │
       │                                         2. 验证失败
       │                                                  │
       │  3. 返回 401 Unauthorized                       │
       │     WWW-Authenticate: Basic realm="Eureka"      │
       │◀────────────────────────────────────────────────┤
       │                                                  │
       │  4. 发送请求（带认证信息）                        │
       │     GET /eureka/apps                            │
       │     Authorization: Basic YWRtaW46MTIzNDU2       │
       ├────────────────────────────────────────────────▶│
       │                                                  │
       │                                         5. 验证成功
       │                                         6. 处理请求
       │                                                  │
       │  7. 返回 200 OK                                 │
       │◀────────────────────────────────────────────────┤
       │                                                  │
```

**Authorization头格式**：

```
Authorization: Basic base64(username:password)

示例：
用户名：admin
密码：123456
Base64编码：YWRtaW46MTIzNDU2

完整头：
Authorization: Basic YWRtaW46MTIzNDU2
```

### 2.2 OAuth2认证

**原理**：

- 使用OAuth2协议进行认证
- 客户端先获取Access Token
- 使用Access Token访问资源

**优点**：

- 安全性高
- 支持多种授权模式
- 适合对外开放的API

**缺点**：

- 实现复杂
- 需要额外的认证服务器
- 性能开销较大

**适用场景**：

- 对外开放的API
- 需要细粒度权限控制
- 多租户系统

### 2.3 自定义认证

**原理**：

- 实现自定义的认证逻辑
- 可以集成企业内部的认证系统

**优点**：

- 灵活性高
- 可以满足特殊需求

**缺点**：

- 开发成本高
- 需要自己维护

---

## 三、HTTP Basic认证原理

### 3.1 认证流程详解

```java
// 1. 客户端发送请求
GET /eureka/apps HTTP/1.1
Host: localhost:8761

// 2. 服务端返回401
HTTP/1.1 401 Unauthorized
WWW-Authenticate: Basic realm="Eureka Server"

// 3. 客户端重新发送请求（带认证信息）
GET /eureka/apps HTTP/1.1
Host: localhost:8761
Authorization: Basic YWRtaW46MTIzNDU2

// 4. 服务端验证
// 4.1 解析Authorization头
String authHeader = request.getHeader("Authorization");
// authHeader = "Basic YWRtaW46MTIzNDU2"

// 4.2 提取Base64编码的凭证
String base64Credentials = authHeader.substring("Basic ".length());
// base64Credentials = "YWRtaW46MTIzNDU2"

// 4.3 Base64解码
byte[] credDecoded = Base64.getDecoder().decode(base64Credentials);
String credentials = new String(credDecoded, StandardCharsets.UTF_8);
// credentials = "admin:123456"

// 4.4 分割用户名和密码
String[] values = credentials.split(":", 2);
String username = values[0];  // "admin"
String password = values[1];  // "123456"

// 4.5 验证用户名和密码
if (authenticate(username, password)) {
    // 验证成功，返回200
    return Response.ok().build();
} else {
    // 验证失败，返回401
    return Response.status(401).build();
}
```

### 3.2 Spring Security认证流程

```
┌─────────────────────────────────────────────────────────┐
│              Spring Security认证流程                     │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  1. 请求到达                                             │
│     ↓                                                   │
│  2. FilterChainProxy（过滤器链代理）                     │
│     ↓                                                   │
│  3. BasicAuthenticationFilter（Basic认证过滤器）         │
│     ├─ 提取Authorization头                              │
│     ├─ 解析用户名和密码                                  │
│     └─ 创建UsernamePasswordAuthenticationToken          │
│     ↓                                                   │
│  4. AuthenticationManager（认证管理器）                  │
│     ├─ 选择合适的AuthenticationProvider                 │
│     └─ 委托给Provider进行认证                            │
│     ↓                                                   │
│  5. DaoAuthenticationProvider（DAO认证提供者）           │
│     ├─ 调用UserDetailsService加载用户信息                │
│     ├─ 使用PasswordEncoder验证密码                       │
│     └─ 返回Authentication对象                            │
│     ↓                                                   │
│  6. SecurityContextHolder（安全上下文持有者）            │
│     └─ 保存Authentication到SecurityContext              │
│     ↓                                                   │
│  7. 继续执行后续过滤器和业务逻辑                          │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

### 3.3 核心组件

#### 1. BasicAuthenticationFilter

```java
public class BasicAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                   HttpServletResponse response,
                                   FilterChain chain) throws ServletException, IOException {

        // 1. 提取Authorization头
        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith("Basic ")) {
            // 没有认证信息，继续执行
            chain.doFilter(request, response);
            return;
        }

        try {
            // 2. 解析认证信息
            String[] tokens = extractAndDecodeHeader(header);
            String username = tokens[0];
            String password = tokens[1];

            // 3. 创建认证令牌
            UsernamePasswordAuthenticationToken authRequest =
                new UsernamePasswordAuthenticationToken(username, password);

            // 4. 进行认证
            Authentication authResult = this.authenticationManager.authenticate(authRequest);

            // 5. 保存认证结果
            SecurityContextHolder.getContext().setAuthentication(authResult);

            // 6. 继续执行
            chain.doFilter(request, response);

        } catch (AuthenticationException e) {
            // 认证失败
            SecurityContextHolder.clearContext();
            this.authenticationEntryPoint.commence(request, response, e);
        }
    }

    private String[] extractAndDecodeHeader(String header) throws IOException {
        byte[] base64Token = header.substring(6).getBytes(StandardCharsets.UTF_8);
        byte[] decoded = Base64.getDecoder().decode(base64Token);
        String token = new String(decoded, StandardCharsets.UTF_8);

        int delim = token.indexOf(":");
        if (delim == -1) {
            throw new BadCredentialsException("Invalid basic authentication token");
        }

        return new String[] {token.substring(0, delim), token.substring(delim + 1)};
    }
}
```

#### 2. AuthenticationManager

```java
public interface AuthenticationManager {
    /**
     * 认证方法
     * @param authentication 认证请求
     * @return 认证结果
     * @throws AuthenticationException 认证失败
     */
    Authentication authenticate(Authentication authentication) throws AuthenticationException;
}

// 默认实现：ProviderManager
public class ProviderManager implements AuthenticationManager {

    private List<AuthenticationProvider> providers;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {

        // 遍历所有的AuthenticationProvider
        for (AuthenticationProvider provider : providers) {

            // 判断Provider是否支持该类型的认证
            if (!provider.supports(authentication.getClass())) {
                continue;
            }

            try {
                // 执行认证
                Authentication result = provider.authenticate(authentication);

                if (result != null) {
                    // 认证成功
                    return result;
                }
            } catch (AuthenticationException e) {
                // 认证失败
                throw e;
            }
        }

        throw new ProviderNotFoundException("No AuthenticationProvider found");
    }
}
```

#### 3. UserDetailsService

```java
public interface UserDetailsService {
    /**
     * 根据用户名加载用户信息
     * @param username 用户名
     * @return 用户详情
     * @throws UsernameNotFoundException 用户不存在
     */
    UserDetails loadUserByUsername(String username) throws UsernameNotFoundException;
}

// 内存实现
public class InMemoryUserDetailsManager implements UserDetailsService {

    private Map<String, UserDetails> users = new HashMap<>();

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserDetails user = users.get(username.toLowerCase());

        if (user == null) {
            throw new UsernameNotFoundException("User not found: " + username);
        }

        return user;
    }

    public void createUser(UserDetails user) {
        users.put(user.getUsername().toLowerCase(), user);
    }
}
```

#### 4. PasswordEncoder

```java
public interface PasswordEncoder {
    /**
     * 加密密码
     * @param rawPassword 原始密码
     * @return 加密后的密码
     */
    String encode(CharSequence rawPassword);

    /**
     * 验证密码
     * @param rawPassword 原始密码
     * @param encodedPassword 加密后的密码
     * @return 是否匹配
     */
    boolean matches(CharSequence rawPassword, String encodedPassword);
}

// BCrypt实现（推荐）
public class BCryptPasswordEncoder implements PasswordEncoder {

    @Override
    public String encode(CharSequence rawPassword) {
        return BCrypt.hashpw(rawPassword.toString(), BCrypt.gensalt());
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        return BCrypt.checkpw(rawPassword.toString(), encodedPassword);
    }
}
```

---

## 四、Eureka集成Spring Security

### 4.1 依赖配置

```xml
<!-- Eureka Server -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-netflix-eureka-server</artifactId>
    <version>2.2.5.RELEASE</version>
</dependency>

<!-- Spring Security -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
```

### 4.2 Security配置

```java
@Configuration
@EnableWebSecurity
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
            // 关闭CSRF（Eureka使用REST API，不需要CSRF保护）
            .csrf().disable()

            // 配置认证规则
            .authorizeRequests()
                // 允许访问健康检查端点（不需要认证）
                .antMatchers("/actuator/**").permitAll()
                // 其他所有请求都需要认证
                .anyRequest().authenticated()
            .and()

            // 启用HTTP Basic认证
            .httpBasic();
    }

    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        // 配置内存用户
        auth.inMemoryAuthentication()
            .passwordEncoder(passwordEncoder())
            .withUser("admin")
                .password(passwordEncoder().encode("123456"))
                .roles("ADMIN")
            .and()
            .withUser("user")
                .password(passwordEncoder().encode("123456"))
                .roles("USER");
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // 使用BCrypt加密
        return new BCryptPasswordEncoder();
    }
}
```

### 4.3 Eureka Server配置

```yaml
server:
  port: 8761

spring:
  application:
    name: eureka-server
  # Spring Security配置
  security:
    user:
      name: admin
      password: 123456

eureka:
  instance:
    hostname: localhost
  client:
    # 不注册自己
    register-with-eureka: false
    # 不拉取服务列表
    fetch-registry: false
    service-url:
      # 注意：这里需要带上用户名和密码
      defaultZone: http://admin:123456@${eureka.instance.hostname}:${server.port}/eureka/
```

### 4.4 Eureka Client配置

```yaml
server:
  port: 8001

spring:
  application:
    name: user-service

eureka:
  client:
    service-url:
      # 连接到Eureka Server，需要带上用户名和密码
      defaultZone: http://admin:123456@localhost:8761/eureka/
  instance:
    prefer-ip-address: true
    instance-id: ${spring.cloud.client.ip-address}:${server.port}
```

---

## 五、认证问题排查

### 5.1 常见问题

#### 问题1：401 Unauthorized

**现象**：

```
com.netflix.discovery.shared.transport.TransportException:
Cannot execute request on any known server
```

**原因**：

- 客户端没有配置用户名和密码
- 用户名或密码错误
- URL格式错误

**解决方案**：

```yaml
# 正确的配置格式
eureka:
  client:
    service-url:
      defaultZone: http://username:password@hostname:port/eureka/

# 示例
eureka:
  client:
    service-url:
      defaultZone: http://admin:123456@localhost:8761/eureka/
```

#### 问题2：CSRF Token错误

**现象**：

```
Could not get response: 403 Forbidden
```

**原因**：

- Spring Security默认启用CSRF保护
- Eureka的REST API不支持CSRF

**解决方案**：

```java
@Override
protected void configure(HttpSecurity http) throws Exception {
    http.csrf().disable();  // 关闭CSRF
}
```

#### 问题3：密码加密问题

**现象**：

```
java.lang.IllegalArgumentException: There is no PasswordEncoder mapped for the id "null"
```

**原因**：

- Spring Security 5.x要求必须指定PasswordEncoder
- 没有配置PasswordEncoder

**解决方案**：

```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}

@Override
protected void configure(AuthenticationManagerBuilder auth) throws Exception {
    auth.inMemoryAuthentication()
        .passwordEncoder(passwordEncoder())  // 指定PasswordEncoder
        .withUser("admin")
        .password(passwordEncoder().encode("123456"))
        .roles("ADMIN");
}
```

### 5.2 调试技巧

#### 1. 开启Security日志

```yaml
logging:
  level:
    org.springframework.security: DEBUG
    org.springframework.cloud.netflix.eureka: DEBUG
```

#### 2. 查看认证信息

```java
@RestController
public class DebugController {

    @GetMapping("/debug/auth")
    public Map<String, Object> getAuthInfo() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        Map<String, Object> info = new HashMap<>();
        info.put("name", auth.getName());
        info.put("authorities", auth.getAuthorities());
        info.put("principal", auth.getPrincipal());
        info.put("authenticated", auth.isAuthenticated());

        return info;
    }
}
```

#### 3. 自定义认证失败处理

```java
@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger logger = LoggerFactory.getLogger(CustomAuthenticationEntryPoint.class);

    @Override
    public void commence(HttpServletRequest request,
                        HttpServletResponse response,
                        AuthenticationException authException) throws IOException {

        // 记录认证失败信息
        logger.error("认证失败: {}", authException.getMessage());
        logger.error("请求URI: {}", request.getRequestURI());
        logger.error("请求方法: {}", request.getMethod());
        logger.error("Authorization头: {}", request.getHeader("Authorization"));

        // 返回401
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"" +
            authException.getMessage() + "\"}");
    }
}

// 配置使用自定义的AuthenticationEntryPoint
@Override
protected void configure(HttpSecurity http) throws Exception {
    http
        .csrf().disable()
        .authorizeRequests()
            .anyRequest().authenticated()
        .and()
        .httpBasic()
            .authenticationEntryPoint(customAuthenticationEntryPoint);  // 使用自定义
}
```

---

## 六、安全最佳实践

### 6.1 密码管理

```java
// 1. 使用强密码
// 不推荐
String password = "123456";

// 推荐
String password = "Eureka@2024!Secure";

// 2. 使用BCrypt加密
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(10);  // 指定强度（默认10）
}

// 3. 从配置文件读取密码
@Value("${eureka.security.password}")
private String password;

// 4. 使用环境变量
String password = System.getenv("EUREKA_PASSWORD");

// 5. 使用配置中心（如Spring Cloud Config）
// 将密码存储在配置中心，支持加密
```

### 6.2 HTTP Basic认证是否必须使用HTTPS？

#### 6.2.1 技术上的要求

**答案：不是强制要求，但强烈建议使用HTTPS**

```
技术层面：
✅ HTTP Basic认证可以在HTTP上工作
✅ 不使用HTTPS也能完成认证流程
✅ Spring Security不会强制要求HTTPS

安全层面：
❌ HTTP传输是明文的
❌ Base64编码可以轻易解码
❌ 密码会在网络中明文传输
❌ 容易被中间人攻击窃取
```

#### 6.2.2 HTTP vs HTTPS对比

**场景1：使用HTTP（不安全）**

```
客户端 ──────────────────────────────────────────────> 服务器
         Authorization: Basic YWRtaW46MTIzNDU2
         ↑
         明文传输，任何人都可以看到
         Base64解码后：admin:123456
         
风险：
1. 网络嗅探工具（如Wireshark）可以直接看到密码
2. 中间人可以截获并重放请求
3. 密码泄露后可以冒充合法用户
```

**场景2：使用HTTPS（安全）**

```
客户端 ──────────────────────────────────────────────> 服务器
         加密的TLS通道
         ↓
         Authorization: Basic YWRtaW46MTIzNDU2
         ↑
         加密传输，无法直接看到内容
         
优势：
1. 整个HTTP请求都被加密
2. 即使被截获也无法解密
3. 防止中间人攻击
4. 保护密码安全
```

#### 6.2.3 实际场景分析

**场景A：内网环境（可以不用HTTPS）**

```yaml
# 适用条件：
# 1. 完全隔离的内网环境
# 2. 没有外部访问
# 3. 网络受信任
# 4. 开发/测试环境

# Eureka Server配置（HTTP）
server:
  port: 8761

spring:
  security:
    user:
      name: admin
      password: 123456

eureka:
  client:
    service-url:
      defaultZone: http://admin:123456@localhost:8761/eureka/

# 风险评估：
# - 内网嗅探风险：低
# - 密码泄露影响：中（仅限内网）
# - 可接受性：开发/测试环境可接受
```

**场景B：生产环境（必须使用HTTPS）**

```yaml
# 适用条件：
# 1. 生产环境
# 2. 跨网络访问
# 3. 有外部访问可能
# 4. 安全要求高

# Eureka Server配置（HTTPS）
server:
  port: 8761
  ssl:
    enabled: true
    key-store: classpath:keystore.p12
    key-store-password: changeit
    key-store-type: PKCS12
    key-alias: eureka

spring:
  security:
    user:
      name: admin
      password: ${EUREKA_PASSWORD}  # 从环境变量读取

eureka:
  instance:
    secure-port-enabled: true
    secure-port: ${server.port}
    non-secure-port-enabled: false
    home-page-url: https://${eureka.instance.hostname}:${server.port}/
    status-page-url: https://${eureka.instance.hostname}:${server.port}/actuator/info
    health-check-url: https://${eureka.instance.hostname}:${server.port}/actuator/health
  client:
    service-url:
      defaultZone: https://admin:${EUREKA_PASSWORD}@localhost:8761/eureka/

# 风险评估：
# - 密码泄露风险：极低
# - 中间人攻击风险：极低
# - 可接受性：生产环境必须
```

#### 6.2.4 不使用HTTPS的风险演示

**使用Wireshark抓包示例**：

```
# HTTP Basic认证在HTTP上的抓包结果

GET /eureka/apps HTTP/1.1
Host: localhost:8761
Authorization: Basic YWRtaW46MTIzNDU2
Accept: application/json

# 任何人都可以：
# 1. 看到Authorization头
# 2. Base64解码：echo "YWRtaW46MTIzNDU2" | base64 -d
# 3. 得到明文密码：admin:123456
# 4. 使用该密码访问Eureka Server

# 攻击示例
curl -u admin:123456 http://localhost:8761/eureka/apps
# 攻击成功！
```

#### 6.2.5 决策树

```
是否需要HTTPS？
  ↓
环境类型？
  ├─ 开发环境
  │   ├─ 本地开发：不需要HTTPS ✅
  │   └─ 团队共享：建议HTTPS ⚠️
  │
  ├─ 测试环境
  │   ├─ 内网隔离：可以不用HTTPS ⚠️
  │   └─ 跨网络：建议HTTPS ⚠️
  │
  └─ 生产环境
      └─ 必须使用HTTPS ❌（不使用HTTPS是严重的安全漏洞）

网络类型？
  ├─ 完全隔离的内网：可以不用HTTPS ⚠️
  ├─ 跨网络/跨机房：必须HTTPS ❌
  └─ 有外部访问：必须HTTPS ❌

安全要求？
  ├─ 低（开发测试）：可以不用HTTPS ⚠️
  ├─ 中（内网生产）：建议HTTPS ⚠️
  └─ 高（公网/敏感）：必须HTTPS ❌
```

#### 6.2.6 替代方案

**如果不想使用HTTPS，可以考虑以下方案**：

```
方案1：VPN隧道
- 在VPN隧道中传输
- VPN提供加密
- 适合跨网络访问

方案2：内网隔离
- 完全隔离的内网环境
- 物理隔离或VLAN隔离
- 无外部访问

方案3：IP白名单
- 限制访问来源IP
- 配合防火墙规则
- 减少暴露面

方案4：API网关
- 在网关层做HTTPS终止
- 内网使用HTTP
- 网关到Eureka使用HTTP

示例：Nginx作为HTTPS终止
upstream eureka {
    server 192.168.1.101:8761;
    server 192.168.1.102:8761;
}

server {
    listen 443 ssl;
    server_name eureka.example.com;
    
    ssl_certificate /path/to/cert.pem;
    ssl_certificate_key /path/to/key.pem;
    
    location / {
        proxy_pass http://eureka;  # 后端使用HTTP
        proxy_set_header Authorization $http_authorization;
    }
}
```

#### 6.2.7 最佳实践建议

```
开发环境：
✅ 可以使用HTTP + Basic认证
✅ 简化配置，提高开发效率
✅ 密码可以使用简单密码（如123456）

测试环境：
⚠️ 建议使用HTTPS + Basic认证
⚠️ 模拟生产环境配置
⚠️ 使用中等强度密码

生产环境：
❌ 必须使用HTTPS + Basic认证
❌ 使用强密码
❌ 密码从环境变量或配置中心读取
❌ 定期更换密码
❌ 启用审计日志

总结：
1. HTTP Basic认证技术上不强制HTTPS
2. 但生产环境强烈建议使用HTTPS
3. 不使用HTTPS会导致密码明文传输
4. 根据实际环境和安全要求做决策
5. 开发环境可以不用HTTPS，生产环境必须用
```

### 6.3 HTTPS配置示例

```yaml
# 生成自签名证书（仅用于测试）
keytool -genkeypair \
  -alias eureka \
  -keyalg RSA \
  -keysize 2048 \
  -storetype PKCS12 \
  -keystore keystore.p12 \
  -validity 3650 \
  -storepass changeit

# Eureka Server HTTPS配置
server:
  port: 8761
  ssl:
    enabled: true
    key-store: classpath:keystore.p12
    key-store-password: changeit
    key-store-type: PKCS12
    key-alias: eureka

eureka:
  instance:
    secure-port-enabled: true
    secure-port: ${server.port}
    non-secure-port-enabled: false
    home-page-url: https://${eureka.instance.hostname}:${server.port}/
    status-page-url: https://${eureka.instance.hostname}:${server.port}/actuator/info
    health-check-url: https://${eureka.instance.hostname}:${server.port}/actuator/health
  client:
    service-url:
      defaultZone: https://admin:${EUREKA_PASSWORD}@localhost:8761/eureka/

# Eureka Client HTTPS配置
eureka:
  client:
    service-url:
      defaultZone: https://admin:${EUREKA_PASSWORD}@localhost:8761/eureka/
```

### 6.4 Actuator端点安全配置

#### 6.4.1 为什么Actuator端点需要鉴权？

**安全风险**：

```
1. 信息泄露风险
   /actuator/env          - 暴露环境变量（可能包含密码、密钥）
   /actuator/configprops  - 暴露配置属性
   /actuator/beans        - 暴露所有Bean信息
   /actuator/mappings     - 暴露所有URL映射
   /actuator/metrics      - 暴露系统指标

2. 操作风险
   /actuator/shutdown     - 可以远程关闭应用
   /actuator/loggers      - 可以修改日志级别
   /actuator/refresh      - 可以刷新配置

3. 性能风险
   /actuator/threaddump   - 生成线程转储，消耗资源
   /actuator/heapdump     - 生成堆转储，消耗大量资源

4. 真实案例
   某公司因为Actuator端点未鉴权，导致：
   - 数据库密码泄露
   - Redis密钥泄露
   - 被恶意关闭服务
```

#### 6.4.2 Actuator端点鉴权配置

**方案1：完全禁止外部访问（推荐用于生产环境）**

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        # 不暴露任何端点
        include: ''
  # 或者只暴露health端点
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      show-details: never  # 不显示详细信息
```

**方案2：只允许内网访问**

```yaml
# application.yml
management:
  server:
    # Actuator使用单独的端口
    port: 9001
    # 只绑定内网IP
    address: 127.0.0.1
  endpoints:
    web:
      exposure:
        include: '*'
```

```java
// 配合防火墙规则
// 只允许内网IP访问9001端口
// iptables -A INPUT -p tcp --dport 9001 -s 192.168.0.0/16 -j ACCEPT
// iptables -A INPUT -p tcp --dport 9001 -j DROP
```

**方案3：配置认证和授权（推荐）**

```java
@Configuration
@EnableWebSecurity
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
            .csrf().disable()
            .authorizeRequests()
                // ========== Actuator端点鉴权配置 ==========
                
                // 1. health端点：允许匿名访问（用于健康检查）
                // 注意：只返回简单的UP/DOWN，不返回详细信息
                .antMatchers("/actuator/health").permitAll()
                
                // 2. info端点：允许认证用户访问
                .antMatchers("/actuator/info").authenticated()
                
                // 3. 危险端点：只允许ADMIN角色访问
                .antMatchers("/actuator/shutdown").hasRole("ADMIN")
                .antMatchers("/actuator/loggers/**").hasRole("ADMIN")
                .antMatchers("/actuator/refresh").hasRole("ADMIN")
                
                // 4. 敏感信息端点：只允许ADMIN角色访问
                .antMatchers("/actuator/env").hasRole("ADMIN")
                .antMatchers("/actuator/configprops").hasRole("ADMIN")
                .antMatchers("/actuator/beans").hasRole("ADMIN")
                .antMatchers("/actuator/mappings").hasRole("ADMIN")
                
                // 5. 性能端点：只允许ADMIN角色访问
                .antMatchers("/actuator/threaddump").hasRole("ADMIN")
                .antMatchers("/actuator/heapdump").hasRole("ADMIN")
                
                // 6. 其他所有Actuator端点：需要认证
                .antMatchers("/actuator/**").authenticated()
                
                // ========== Eureka端点鉴权配置 ==========
                
                // Eureka控制台只允许ADMIN角色访问
                .antMatchers("/").hasRole("ADMIN")
                .antMatchers("/eureka/**").hasRole("ADMIN")

                // 服务注册和发现允许USER和ADMIN角色
                .antMatchers("/eureka/apps/**").hasAnyRole("USER", "ADMIN")

                // 其他请求需要认证
                .anyRequest().authenticated()
            .and()
            .httpBasic();
    }

    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.inMemoryAuthentication()
            .passwordEncoder(passwordEncoder())
            // 管理员账号（可以访问所有端点）
            .withUser("admin")
                .password(passwordEncoder().encode("Admin@2024"))
                .roles("ADMIN")
            .and()
            // 普通服务账号（只能注册和发现服务）
            .withUser("service")
                .password(passwordEncoder().encode("Service@2024"))
                .roles("USER")
            .and()
            // 监控账号（只能访问监控端点）
            .withUser("monitor")
                .password(passwordEncoder().encode("Monitor@2024"))
                .roles("MONITOR");
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

**方案4：更细粒度的角色控制**

```java
@Configuration
@EnableWebSecurity
public class ActuatorSecurityConfig extends WebSecurityConfigurerAdapter {

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
            .csrf().disable()
            .authorizeRequests()
                // 健康检查：允许匿名访问（Kubernetes/负载均衡器需要）
                .antMatchers("/actuator/health", "/actuator/health/**").permitAll()
                
                // 只读监控端点：MONITOR角色可访问
                .antMatchers(HttpMethod.GET, "/actuator/metrics/**").hasAnyRole("MONITOR", "ADMIN")
                .antMatchers(HttpMethod.GET, "/actuator/prometheus").hasAnyRole("MONITOR", "ADMIN")
                
                // 危险操作端点：只有ADMIN可访问
                .antMatchers("/actuator/shutdown").hasRole("ADMIN")
                .antMatchers(HttpMethod.POST, "/actuator/loggers/**").hasRole("ADMIN")
                .antMatchers(HttpMethod.POST, "/actuator/refresh").hasRole("ADMIN")
                
                // 敏感信息端点：ADMIN和DEVOPS可访问
                .antMatchers("/actuator/env/**").hasAnyRole("ADMIN", "DEVOPS")
                .antMatchers("/actuator/configprops").hasAnyRole("ADMIN", "DEVOPS")
                
                // 其他Actuator端点：需要认证
                .antMatchers("/actuator/**").authenticated()
                
                // Eureka端点
                .antMatchers("/eureka/apps/**").hasAnyRole("USER", "ADMIN")
                .antMatchers("/**").hasRole("ADMIN")
            .and()
            .httpBasic();
    }

    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.inMemoryAuthentication()
            .passwordEncoder(passwordEncoder())
            .withUser("admin")
                .password(passwordEncoder().encode("Admin@2024"))
                .roles("ADMIN")
            .and()
            .withUser("service")
                .password(passwordEncoder().encode("Service@2024"))
                .roles("USER")
            .and()
            .withUser("monitor")
                .password(passwordEncoder().encode("Monitor@2024"))
                .roles("MONITOR")
            .and()
            .withUser("devops")
                .password(passwordEncoder().encode("DevOps@2024"))
                .roles("DEVOPS");
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

#### 6.4.3 Actuator配置最佳实践

```yaml
# application.yml - 生产环境推荐配置

management:
  # 使用单独的端口
  server:
    port: 9001
    # 只绑定内网地址
    address: 127.0.0.1
  
  endpoints:
    web:
      # 修改基础路径（增加安全性）
      base-path: /management
      exposure:
        # 只暴露必要的端点
        include: health,info,metrics,prometheus
        # 明确排除危险端点
        exclude: shutdown,threaddump,heapdump
  
  endpoint:
    health:
      # 健康检查详情只对认证用户显示
      show-details: when-authorized
      # 配置角色
      roles: ADMIN,MONITOR
    
    shutdown:
      # 禁用shutdown端点
      enabled: false
    
    env:
      # 脱敏敏感信息
      keys-to-sanitize: password,secret,key,token,.*credentials.*,vcap_services
```

#### 6.4.4 访问示例

```bash
# 1. 匿名访问health（允许）
curl http://localhost:8761/actuator/health
# 返回：{"status":"UP"}

# 2. 匿名访问metrics（拒绝）
curl http://localhost:8761/actuator/metrics
# 返回：401 Unauthorized

# 3. 使用monitor账号访问metrics（允许）
curl -u monitor:Monitor@2024 http://localhost:8761/actuator/metrics
# 返回：指标列表

# 4. 使用monitor账号访问env（拒绝）
curl -u monitor:Monitor@2024 http://localhost:8761/actuator/env
# 返回：403 Forbidden

# 5. 使用admin账号访问env（允许）
curl -u admin:Admin@2024 http://localhost:8761/actuator/env
# 返回：环境变量（敏感信息已脱敏）

# 6. 使用admin账号关闭应用（如果启用）
curl -X POST -u admin:Admin@2024 http://localhost:8761/actuator/shutdown
# 返回：{"message":"Shutting down..."}
```

#### 6.4.5 Prometheus监控集成

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  endpoint:
    prometheus:
      enabled: true
  metrics:
    export:
      prometheus:
        enabled: true
```

```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'eureka-server'
    metrics_path: '/actuator/prometheus'
    basic_auth:
      username: 'monitor'
      password: 'Monitor@2024'
    static_configs:
      - targets: ['localhost:8761']
```

#### 6.4.6 完全禁止Actuator端点（最安全）

**如果项目不允许开放任何Actuator端点**：

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        # 不暴露任何端点
        include: ''
  # 或者完全禁用
  endpoint:
    health:
      enabled: false
    info:
      enabled: false
```

```java
@Configuration
@EnableWebSecurity
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
            .csrf().disable()
            .authorizeRequests()
                // 完全禁止访问Actuator端点
                .antMatchers("/actuator/**").denyAll()
                
                // Eureka端点配置
                .antMatchers("/eureka/apps/**").hasAnyRole("USER", "ADMIN")
                .antMatchers("/**").hasRole("ADMIN")
            .and()
            .httpBasic();
    }
}
```

**健康检查替代方案**：

```java
// 自定义健康检查端点（不使用Actuator）
@RestController
public class CustomHealthController {
    
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.ok(health);
    }
}
```

#### 6.4.7 安全检查清单

```
✅ Actuator端点安全检查清单：

1. 端点暴露
   □ 只暴露必要的端点
   □ 禁用危险端点（shutdown、heapdump）
   □ 生产环境不暴露敏感端点

2. 访问控制
   □ 所有端点都配置了认证
   □ 敏感端点配置了角色授权
   □ health端点考虑是否允许匿名访问

3. 网络隔离
   □ 使用单独的端口
   □ 绑定内网地址
   □ 配置防火墙规则

4. 信息脱敏
   □ 配置keys-to-sanitize
   □ health详情只对授权用户显示
   □ 不暴露敏感配置

5. 监控集成
   □ Prometheus使用认证
   □ 监控账号权限最小化
   □ 定期审计访问日志

6. 应急预案
   □ 发现泄露立即禁用端点
   □ 更换泄露的密码和密钥
   □ 审计访问日志
```

### 6.5 基于Nginx的访问控制

#### 6.5.1 为什么使用Nginx进行访问控制？

**优势**：

```
1. 集中管理
   - 统一的访问控制策略
   - 无需修改应用代码
   - 便于维护和审计

2. 性能优势
   - Nginx处理静态请求效率高
   - 减轻应用服务器压力
   - 支持高并发

3. 灵活性
   - IP白名单/黑名单
   - 地理位置限制
   - 请求频率限制
   - SSL/TLS终止

4. 安全性
   - 隐藏后端服务器信息
   - 防止DDoS攻击
   - 请求过滤和验证
```

#### 6.5.2 Nginx配置方案

**方案1：基于IP白名单的访问控制**

```nginx
# /etc/nginx/conf.d/eureka.conf

upstream eureka_servers {
    # 后端Eureka Server集群
    server 192.168.1.101:8761 weight=1 max_fails=2 fail_timeout=30s;
    server 192.168.1.102:8761 weight=1 max_fails=2 fail_timeout=30s;
    server 192.168.1.103:8761 weight=1 max_fails=2 fail_timeout=30s;
    
    # 保持会话一致性
    ip_hash;
}

server {
    listen 80;
    server_name eureka.example.com;
    
    # 访问日志
    access_log /var/log/nginx/eureka_access.log;
    error_log /var/log/nginx/eureka_error.log;
    
    # ========== IP白名单配置 ==========
    
    # 允许内网IP段访问
    allow 192.168.0.0/16;
    allow 10.0.0.0/8;
    allow 172.16.0.0/12;
    
    # 允许特定公网IP（如办公网络）
    allow 203.0.113.0/24;
    
    # 拒绝其他所有IP
    deny all;
    
    # ========== Eureka控制台访问控制 ==========
    
    location / {
        # 只允许管理员IP访问控制台
        allow 192.168.1.100;  # 管理员IP
        deny all;
        
        proxy_pass http://eureka_servers;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
    
    # ========== Eureka API访问控制 ==========
    
    location /eureka/ {
        # 允许服务注册和发现
        # 内网IP可以访问
        allow 192.168.0.0/16;
        deny all;
        
        proxy_pass http://eureka_servers;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
    
    # ========== 禁止访问Actuator端点 ==========
    
    location /actuator/ {
        # 完全禁止外部访问
        deny all;
        return 403;
    }
}
```

**方案2：基于Basic认证的访问控制**

```nginx
# /etc/nginx/conf.d/eureka.conf

upstream eureka_servers {
    server 192.168.1.101:8761;
    server 192.168.1.102:8761;
    server 192.168.1.103:8761;
}

server {
    listen 80;
    server_name eureka.example.com;
    
    # ========== Nginx Basic认证 ==========
    
    # 创建密码文件
    # htpasswd -c /etc/nginx/.htpasswd admin
    # htpasswd /etc/nginx/.htpasswd service
    
    location / {
        # 启用Basic认证
        auth_basic "Eureka Server";
        auth_basic_user_file /etc/nginx/.htpasswd;
        
        proxy_pass http://eureka_servers;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        
        # 传递认证信息到后端
        proxy_set_header Authorization $http_authorization;
        proxy_pass_header Authorization;
    }
    
    # 健康检查端点不需要认证
    location /actuator/health {
        auth_basic off;
        proxy_pass http://eureka_servers;
    }
}
```

**方案3：HTTPS + IP白名单 + Basic认证（推荐生产环境）**

```nginx
# /etc/nginx/conf.d/eureka.conf

upstream eureka_servers {
    server 192.168.1.101:8761;
    server 192.168.1.102:8761;
    server 192.168.1.103:8761;
}

# HTTP重定向到HTTPS
server {
    listen 80;
    server_name eureka.example.com;
    return 301 https://$server_name$request_uri;
}

# HTTPS配置
server {
    listen 443 ssl http2;
    server_name eureka.example.com;
    
    # SSL证书配置
    ssl_certificate /etc/nginx/ssl/eureka.crt;
    ssl_certificate_key /etc/nginx/ssl/eureka.key;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;
    ssl_prefer_server_ciphers on;
    
    # 访问日志
    access_log /var/log/nginx/eureka_access.log;
    error_log /var/log/nginx/eureka_error.log;
    
    # ========== Eureka控制台（需要认证 + IP限制） ==========
    
    location / {
        # IP白名单
        allow 192.168.1.100;  # 管理员IP
        deny all;
        
        # Basic认证
        auth_basic "Eureka Admin Console";
        auth_basic_user_file /etc/nginx/.htpasswd.admin;
        
        proxy_pass http://eureka_servers;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
    }
    
    # ========== Eureka API（只需IP限制） ==========
    
    location /eureka/ {
        # 允许内网服务访问
        allow 192.168.0.0/16;
        deny all;
        
        proxy_pass http://eureka_servers;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
    }
    
    # ========== 健康检查（允许负载均衡器访问） ==========
    
    location /actuator/health {
        # 允许负载均衡器IP
        allow 192.168.1.200;
        deny all;
        
        proxy_pass http://eureka_servers;
    }
    
    # ========== 禁止其他Actuator端点 ==========
    
    location /actuator/ {
        deny all;
        return 403;
    }
}
```

**方案4：请求频率限制（防止DDoS）**

```nginx
# /etc/nginx/conf.d/eureka.conf

# 定义请求频率限制区域
limit_req_zone $binary_remote_addr zone=eureka_limit:10m rate=10r/s;
limit_conn_zone $binary_remote_addr zone=eureka_conn:10m;

upstream eureka_servers {
    server 192.168.1.101:8761;
    server 192.168.1.102:8761;
    server 192.168.1.103:8761;
}

server {
    listen 443 ssl;
    server_name eureka.example.com;
    
    # SSL配置...
    
    location / {
        # 限制请求频率：每秒10个请求，突发20个
        limit_req zone=eureka_limit burst=20 nodelay;
        
        # 限制并发连接数：每个IP最多10个连接
        limit_conn eureka_conn 10;
        
        # IP白名单
        allow 192.168.0.0/16;
        deny all;
        
        proxy_pass http://eureka_servers;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```

#### 6.5.3 创建Nginx密码文件

```bash
# 安装htpasswd工具
# CentOS/RHEL
yum install -y httpd-tools

# Ubuntu/Debian
apt-get install -y apache2-utils

# 创建密码文件（第一个用户）
htpasswd -c /etc/nginx/.htpasswd admin
# 输入密码：Admin@2024

# 添加更多用户（不使用-c参数）
htpasswd /etc/nginx/.htpasswd service
# 输入密码：Service@2024

# 查看密码文件
cat /etc/nginx/.htpasswd
# admin:$apr1$xxx...
# service:$apr1$yyy...

# 设置文件权限
chmod 600 /etc/nginx/.htpasswd
chown nginx:nginx /etc/nginx/.htpasswd
```

#### 6.5.4 Nginx配置测试和重载

```bash
# 测试配置文件语法
nginx -t

# 重载配置（不中断服务）
nginx -s reload

# 查看Nginx状态
systemctl status nginx

# 查看访问日志
tail -f /var/log/nginx/eureka_access.log

# 查看错误日志
tail -f /var/log/nginx/eureka_error.log
```

### 6.6 基于Filter的访问控制

#### 6.6.1 自定义IP白名单Filter

```java
package com.example.eureka.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * IP白名单过滤器
 */
@Component
public class IpWhitelistFilter implements Filter {
    
    private static final Logger logger = LoggerFactory.getLogger(IpWhitelistFilter.class);
    
    // 从配置文件读取白名单
    @Value("${security.ip.whitelist:}")
    private String whitelistConfig;
    
    private Set<String> whitelist = new HashSet<>();
    
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // 解析IP白名单
        if (whitelistConfig != null && !whitelistConfig.isEmpty()) {
            String[] ips = whitelistConfig.split(",");
            whitelist.addAll(Arrays.asList(ips));
            logger.info("IP白名单已加载: {}", whitelist);
        }
    }
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        // 获取客户端IP
        String clientIp = getClientIp(httpRequest);
        
        // 检查是否在白名单中
        if (!isIpAllowed(clientIp)) {
            logger.warn("拒绝访问 - IP: {}, URI: {}", clientIp, httpRequest.getRequestURI());
            httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
            httpResponse.setContentType("application/json;charset=UTF-8");
            httpResponse.getWriter().write("{\"error\":\"Access Denied\",\"message\":\"Your IP is not allowed\"}");
            return;
        }
        
        logger.debug("允许访问 - IP: {}, URI: {}", clientIp, httpRequest.getRequestURI());
        chain.doFilter(request, response);
    }
    
    /**
     * 获取客户端真实IP
     */
    private String getClientIp(HttpServletRequest request) {
        // 优先从X-Forwarded-For获取（经过代理的情况）
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            // X-Forwarded-For可能包含多个IP，取第一个
            int index = ip.indexOf(',');
            if (index != -1) {
                return ip.substring(0, index);
            }
            return ip;
        }
        
        // 从X-Real-IP获取
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }
        
        // 直接从请求获取
        return request.getRemoteAddr();
    }
    
    /**
     * 检查IP是否允许访问
     */
    private boolean isIpAllowed(String ip) {
        // 如果白名单为空，允许所有IP（开发模式）
        if (whitelist.isEmpty()) {
            return true;
        }
        
        // 检查精确匹配
        if (whitelist.contains(ip)) {
            return true;
        }
        
        // 检查IP段匹配（如192.168.1.0/24）
        for (String allowedIp : whitelist) {
            if (allowedIp.contains("/")) {
                // CIDR格式
                if (isIpInCidr(ip, allowedIp)) {
                    return true;
                }
            } else if (allowedIp.endsWith(".*")) {
                // 通配符格式（如192.168.1.*）
                String prefix = allowedIp.substring(0, allowedIp.length() - 1);
                if (ip.startsWith(prefix)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * 检查IP是否在CIDR范围内
     */
    private boolean isIpInCidr(String ip, String cidr) {
        try {
            String[] parts = cidr.split("/");
            String network = parts[0];
            int prefixLength = Integer.parseInt(parts[1]);
            
            long ipLong = ipToLong(ip);
            long networkLong = ipToLong(network);
            long mask = -1L << (32 - prefixLength);
            
            return (ipLong & mask) == (networkLong & mask);
        } catch (Exception e) {
            logger.error("CIDR解析错误: {}", cidr, e);
            return false;
        }
    }
    
    /**
     * IP地址转换为长整型
     */
    private long ipToLong(String ip) {
        String[] parts = ip.split("\\.");
        long result = 0;
        for (int i = 0; i < 4; i++) {
            result = (result << 8) | Integer.parseInt(parts[i]);
        }
        return result;
    }
    
    @Override
    public void destroy() {
        whitelist.clear();
    }
}
```

#### 6.6.2 配置Filter

```java
package com.example.eureka.config;

import com.example.eureka.filter.IpWhitelistFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FilterConfig {
    
    @Bean
    public FilterRegistrationBean<IpWhitelistFilter> ipWhitelistFilter(IpWhitelistFilter filter) {
        FilterRegistrationBean<IpWhitelistFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        
        // 配置过滤的URL模式
        registration.addUrlPatterns("/*");
        
        // 设置过滤器顺序（数字越小优先级越高）
        registration.setOrder(1);
        
        registration.setName("ipWhitelistFilter");
        
        return registration;
    }
}
```

#### 6.6.3 配置文件

```yaml
# application.yml
security:
  ip:
    # IP白名单配置（支持多种格式）
    whitelist: >
      127.0.0.1,
      192.168.1.100,
      192.168.1.0/24,
      10.0.0.*
```

#### 6.6.4 基于路径的访问控制Filter

```java
package com.example.eureka.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 基于路径的访问控制过滤器
 */
@Component
public class PathAccessControlFilter implements Filter {
    
    private static final Logger logger = LoggerFactory.getLogger(PathAccessControlFilter.class);
    
    // 路径访问控制规则
    private Map<String, Set<String>> pathIpRules = new HashMap<>();
    
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // 配置不同路径的IP白名单
        
        // Eureka控制台：只允许管理员IP
        Set<String> adminIps = new HashSet<>();
        adminIps.add("192.168.1.100");
        pathIpRules.put("/", adminIps);
        
        // Eureka API：允许内网IP
        Set<String> serviceIps = new HashSet<>();
        serviceIps.add("192.168.1.0/24");
        serviceIps.add("10.0.0.0/8");
        pathIpRules.put("/eureka/", serviceIps);
        
        // Actuator端点：完全禁止
        Set<String> noAccess = new HashSet<>();
        pathIpRules.put("/actuator/", noAccess);
        
        logger.info("路径访问控制规则已加载");
    }
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        String uri = httpRequest.getRequestURI();
        String clientIp = getClientIp(httpRequest);
        
        // 检查路径访问权限
        for (Map.Entry<String, Set<String>> entry : pathIpRules.entrySet()) {
            String pathPrefix = entry.getKey();
            Set<String> allowedIps = entry.getValue();
            
            if (uri.startsWith(pathPrefix)) {
                // 如果白名单为空，表示完全禁止访问
                if (allowedIps.isEmpty()) {
                    logger.warn("路径被禁止访问 - IP: {}, URI: {}", clientIp, uri);
                    httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    httpResponse.getWriter().write("{\"error\":\"Access Denied\"}");
                    return;
                }
                
                // 检查IP是否在白名单中
                if (!isIpAllowed(clientIp, allowedIps)) {
                    logger.warn("IP不在白名单中 - IP: {}, URI: {}", clientIp, uri);
                    httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    httpResponse.getWriter().write("{\"error\":\"Access Denied\"}");
                    return;
                }
                
                break;
            }
        }
        
        chain.doFilter(request, response);
    }
    
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            int index = ip.indexOf(',');
            if (index != -1) {
                return ip.substring(0, index);
            }
            return ip;
        }
        
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }
        
        return request.getRemoteAddr();
    }
    
    private boolean isIpAllowed(String ip, Set<String> allowedIps) {
        for (String allowedIp : allowedIps) {
            if (allowedIp.contains("/")) {
                // CIDR格式
                if (isIpInCidr(ip, allowedIp)) {
                    return true;
                }
            } else if (allowedIp.equals(ip)) {
                return true;
            }
        }
        return false;
    }
    
    private boolean isIpInCidr(String ip, String cidr) {
        // CIDR匹配逻辑（同上）
        try {
            String[] parts = cidr.split("/");
            String network = parts[0];
            int prefixLength = Integer.parseInt(parts[1]);
            
            long ipLong = ipToLong(ip);
            long networkLong = ipToLong(network);
            long mask = -1L << (32 - prefixLength);
            
            return (ipLong & mask) == (networkLong & mask);
        } catch (Exception e) {
            return false;
        }
    }
    
    private long ipToLong(String ip) {
        String[] parts = ip.split("\\.");
        long result = 0;
        for (int i = 0; i < 4; i++) {
            result = (result << 8) | Integer.parseInt(parts[i]);
        }
        return result;
    }
    
    @Override
    public void destroy() {
        pathIpRules.clear();
    }
}
```

#### 6.6.5 访问频率限制Filter

```java
package com.example.eureka.filter;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * 访问频率限制过滤器（基于Guava RateLimiter）
 */
@Component
public class RateLimitFilter implements Filter {
    
    private static final Logger logger = LoggerFactory.getLogger(RateLimitFilter.class);
    
    // 每个IP每秒允许10个请求
    private static final double PERMITS_PER_SECOND = 10.0;
    
    // IP限流器缓存（1小时过期）
    private LoadingCache<String, RateLimiter> limiters;
    
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        limiters = CacheBuilder.newBuilder()
            .maximumSize(10000)
            .expireAfterAccess(1, TimeUnit.HOURS)
            .build(new CacheLoader<String, RateLimiter>() {
                @Override
                public RateLimiter load(String key) {
                    return RateLimiter.create(PERMITS_PER_SECOND);
                }
            });
        
        logger.info("访问频率限制已启用: {} 请求/秒", PERMITS_PER_SECOND);
    }
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        String clientIp = getClientIp(httpRequest);
        
        try {
            RateLimiter limiter = limiters.get(clientIp);
            
            // 尝试获取令牌（非阻塞）
            if (!limiter.tryAcquire()) {
                logger.warn("请求频率超限 - IP: {}, URI: {}", clientIp, httpRequest.getRequestURI());
                httpResponse.setStatus(429); // Too Many Requests
                httpResponse.setContentType("application/json;charset=UTF-8");
                httpResponse.getWriter().write("{\"error\":\"Too Many Requests\",\"message\":\"Rate limit exceeded\"}");
                return;
            }
            
            chain.doFilter(request, response);
            
        } catch (ExecutionException e) {
            logger.error("限流器获取失败", e);
            chain.doFilter(request, response);
        }
    }
    
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            int index = ip.indexOf(',');
            if (index != -1) {
                return ip.substring(0, index);
            }
            return ip;
        }
        
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }
        
        return request.getRemoteAddr();
    }
    
    @Override
    public void destroy() {
        limiters.invalidateAll();
    }
}
```

**添加Guava依赖**：

```xml
<dependency>
    <groupId>com.google.guava</groupId>
    <artifactId>guava</artifactId>
    <version>30.1-jre</version>
</dependency>
```

#### 6.6.6 Filter方案对比

```
方案对比：

Nginx方案：
✅ 性能好（C语言实现）
✅ 集中管理
✅ 不影响应用代码
✅ 支持SSL终止
❌ 需要额外部署Nginx
❌ 配置相对复杂

Filter方案：
✅ 无需额外组件
✅ 配置灵活（代码控制）
✅ 易于调试
✅ 可以访问应用上下文
❌ 性能略低于Nginx
❌ 每个应用都需要配置

推荐方案：
- 生产环境：Nginx + Filter（双重保护）
- 开发环境：Filter（简单快速）
- 高并发场景：Nginx（性能优先）
```

### 6.7 审计日志

```java
@Component
@Slf4j
public class AuditLogger {

    @EventListener
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        Authentication auth = event.getAuthentication();
        log.info("认证成功 - 用户: {}, IP: {}, 时间: {}",
            auth.getName(),
            getClientIP(),
            LocalDateTime.now());
    }

    @EventListener
    public void onAuthenticationFailure(AbstractAuthenticationFailureEvent event) {
        Authentication auth = event.getAuthentication();
        Exception exception = event.getException();
        log.warn("认证失败 - 用户: {}, IP: {}, 原因: {}, 时间: {}",
            auth.getName(),
            getClientIP(),
            exception.getMessage(),
            LocalDateTime.now());
    }

    private String getClientIP() {
        ServletRequestAttributes attributes =
            (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            return request.getRemoteAddr();
        }
        return "unknown";
    }
}
```

---

## 七、总结

### 7.1 核心要点

```
1. Eureka支持多种认证方式，HTTP Basic认证最简单实用
2. Spring Security提供了完整的认证和授权框架
3. 生产环境必须启用安全认证，防止恶意访问
4. HTTP Basic认证技术上不强制HTTPS，但生产环境强烈建议使用
5. 开发/测试环境可以使用HTTP，生产环境必须使用HTTPS
6. Actuator端点必须配置鉴权，防止信息泄露和恶意操作
7. 可以使用Nginx进行集中的访问控制（IP白名单、频率限制）
8. 可以使用Filter进行应用级的访问控制（灵活可定制）
9. 推荐Nginx + Filter双重保护，提高安全性
10. 记录审计日志，便于追踪和排查问题
```

### 7.2 配置清单

```yaml
# Eureka Server配置
spring:
  security:
    user:
      name: admin
      password: 123456

eureka:
  client:
    service-url:
      defaultZone: http://admin:123456@localhost:8761/eureka/

# Eureka Client配置
eureka:
  client:
    service-url:
      defaultZone: http://admin:123456@localhost:8761/eureka/
```

### 7.3 注意事项

```
1. 密码不要硬编码在代码中
2. 使用强密码，定期更换
3. 生产环境强烈建议使用HTTPS（开发环境可以不用）
4. HTTP Basic认证可以在HTTP上工作，但会有安全风险
5. 关闭CSRF保护（Eureka REST API不需要）
6. 配置PasswordEncoder（Spring Security 5.x必须）
7. URL格式：http://username:password@hostname:port/eureka/
8. 特殊字符需要URL编码
9. 根据环境类型和安全要求决定是否使用HTTPS
```

---

**下一篇**：[03-Eureka端口鉴权实战.md](./03-Eureka端口鉴权实战.md) - 详细的端口鉴权配置和代码实现
