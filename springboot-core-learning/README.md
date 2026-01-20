# Spring 核心学习项目

> 整合所有 Spring 核心相关的学习模块，包括注解、IOC、AOP、Spring Boot 特性等

## 📁 项目结构

```
springboot-core-learning/
├── spring-annotation          # Spring 注解学习
├── spring-ioc                 # Spring IOC 容器学习
└── springboot-learning        # Spring Boot 特性学习
```

---

## 🎯 学习目标

### 1. Spring 注解
- **spring-annotation** - Spring 注解深度学习
  - Bean 作用域（Singleton、Prototype、Request、Session）
  - 依赖注入（@Autowired、@Resource、@Inject）
  - Bean 获取方式（getBean、ObjectProvider）
  - 递归场景下的状态隔离
  - 完整的文档和示例代码

### 2. Spring IOC
- **spring-ioc** - Spring IOC 容器原理
  - IOC 容器的初始化过程
  - Bean 的生命周期
  - BeanFactory vs ApplicationContext
  - BeanPostProcessor 扩展点
  - 循环依赖的解决

### 3. Spring Boot 特性
- **springboot-learning** - Spring Boot 核心特性
  - 自动配置原理
  - Starter 机制
  - 条件注解（@Conditional）
  - 配置文件加载
  - 监听器和事件机制

---

## 🚀 快速开始

### 环境要求

- JDK 1.8+
- Maven 3.6+
- Spring Boot 2.2.5+

### 启动步骤

1. **进入具体模块**
   ```bash
   cd spring-annotation
   # 或
   cd spring-ioc
   # 或
   cd springboot-learning
   ```

2. **运行项目**
   ```bash
   mvn spring-boot:run
   ```

---

## 📚 学习路径

### 🔰 初学者路径

1. **Spring 注解基础**
   - 学习 `spring-annotation` 模块
   - 阅读文档：[01-Spring-Bean作用域与递归隔离完整指南](./spring-annotation/docs/01-Spring-Bean作用域与递归隔离完整指南.md)
   - 理解 Bean 的作用域和生命周期

2. **Spring IOC 容器**
   - 学习 `spring-ioc` 模块
   - 理解 IOC 的核心概念
   - 掌握依赖注入的方式

3. **Spring Boot 入门**
   - 学习 `springboot-learning` 模块
   - 理解自动配置的原理
   - 掌握 Starter 的使用

### 🚀 进阶路径

1. **深入注解原理**
   - 阅读 Spring 注解的源码
   - 理解元注解的作用
   - 自定义注解的实现

2. **IOC 容器扩展**
   - BeanFactoryPostProcessor
   - BeanPostProcessor
   - ApplicationContextAware
   - 自定义 Scope

3. **Spring Boot 高级特性**
   - 自定义 Starter
   - 自定义自动配置
   - 条件注解的使用
   - SPI 机制

---

## 🔧 技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| Spring Framework | 5.2.4.RELEASE | 核心框架 |
| Spring Boot | 2.2.5.RELEASE | 快速开发框架 |
| JDK | 1.8+ | Java 开发环境 |
| Maven | 3.6+ | 项目管理工具 |

---

## 📖 各模块详细说明

### 1. spring-annotation

**功能**：Spring 注解深度学习

**核心内容**：
- Bean 作用域详解
  - Singleton（单例）
  - Prototype（原型）
  - Request（请求）
  - Session（会话）

- 依赖注入方式
  - 构造器注入
  - Setter 注入
  - 字段注入
  - ObjectProvider 延迟注入

- Bean 获取方式对比
  - ApplicationContext.getBean()
  - ObjectProvider.getObject()
  - @Autowired 自动注入

- 递归场景处理
  - 状态隔离的实现
  - 快照恢复机制
  - 性能优化策略

**文档目录**：
- [01-Spring-Bean作用域与递归隔离完整指南](./spring-annotation/docs/01-Spring-Bean作用域与递归隔离完整指南.md)
- [02-递归场景下的状态隔离核心原理](./spring-annotation/docs/02-递归场景下的状态隔离核心原理.md)
- [03-getBean与ObjectProvider详解](./spring-annotation/docs/03-getBean与ObjectProvider详解.md)
- [04-快速参考指南](./spring-annotation/docs/04-快速参考指南.md)

---

### 2. spring-ioc

**功能**：Spring IOC 容器原理学习

**核心内容**：
- IOC 容器的初始化
  - BeanDefinition 的注册
  - Bean 的实例化
  - 依赖注入的过程

- Bean 的生命周期
  - 实例化（Instantiation）
  - 属性赋值（Populate）
  - 初始化（Initialization）
  - 销毁（Destruction）

- 容器扩展点
  - BeanFactoryPostProcessor
  - BeanPostProcessor
  - InitializingBean
  - DisposableBean

- 循环依赖
  - 三级缓存机制
  - 构造器循环依赖
  - Setter 循环依赖

**学习要点**：
```java
// Bean 生命周期完整流程
实例化 → 属性赋值 → Aware接口回调 → 
BeanPostProcessor前置处理 → 初始化方法 → 
BeanPostProcessor后置处理 → 使用 → 销毁
```

---

### 3. springboot-learning

**功能**：Spring Boot 核心特性学习

**核心内容**：
- 自动配置原理
  - @EnableAutoConfiguration
  - @Conditional 条件注解
  - spring.factories 文件
  - 自动配置的加载过程

- Starter 机制
  - Starter 的命名规范
  - 自定义 Starter
  - 依赖管理

- 配置文件
  - application.properties
  - application.yml
  - 配置文件的优先级
  - @ConfigurationProperties

- 事件机制
  - ApplicationEvent
  - ApplicationListener
  - @EventListener
  - 异步事件处理

**自动配置流程**：
```
@SpringBootApplication
    ↓
@EnableAutoConfiguration
    ↓
@Import(AutoConfigurationImportSelector.class)
    ↓
读取 META-INF/spring.factories
    ↓
加载自动配置类
    ↓
根据 @Conditional 条件判断是否生效
```

---

## 💡 核心知识点

### Bean 作用域对比

| Scope | 说明 | 创建时机 | 生命周期 | 适用场景 |
|-------|------|----------|----------|----------|
| **Singleton** | 单例 | 容器启动时 | 容器生命周期 | 无状态Bean |
| **Prototype** | 原型 | 每次获取时 | 获取后由调用者管理 | 有状态Bean |
| **Request** | 请求 | 每个HTTP请求 | 请求结束 | Web应用 |
| **Session** | 会话 | 每个HTTP会话 | 会话结束 | Web应用 |

### 依赖注入方式对比

| 方式 | 优点 | 缺点 | 推荐度 |
|------|------|------|--------|
| **构造器注入** | 强制依赖、不可变 | 参数过多时不便 | ⭐⭐⭐⭐⭐ |
| **Setter注入** | 可选依赖、灵活 | 可能忘记注入 | ⭐⭐⭐ |
| **字段注入** | 简洁 | 不利于测试 | ⭐⭐ |

### IOC 容器对比

| 特性 | BeanFactory | ApplicationContext |
|------|-------------|-------------------|
| 功能 | 基础容器 | 高级容器 |
| 加载方式 | 延迟加载 | 立即加载 |
| 国际化 | 不支持 | 支持 |
| 事件机制 | 不支持 | 支持 |
| AOP | 需手动配置 | 自动支持 |

---

## ⚠️ 常见问题

### Q1: Singleton 和 Prototype 如何选择？

**A**: 
- **无状态服务**：使用 Singleton（默认）
- **有状态且需要隔离**：使用 Prototype
- **Web 请求相关**：使用 Request Scope

### Q2: 循环依赖如何解决？

**A**: 
- **Setter 注入**：通过三级缓存解决
- **构造器注入**：无法解决，需要重构代码
- **@Lazy 注解**：延迟注入，打破循环

### Q3: 自动配置如何生效？

**A**: 
1. @SpringBootApplication 包含 @EnableAutoConfiguration
2. 读取 META-INF/spring.factories 文件
3. 加载自动配置类
4. 根据 @Conditional 条件判断是否生效

### Q4: 如何自定义 Starter？

**A**: 
1. 创建自动配置类（@Configuration + @ConditionalOnXxx）
2. 创建 spring.factories 文件
3. 打包成 jar 包
4. 在项目中引入依赖

---

## 📈 学习建议

### 理论学习
1. **先理解概念，再看源码**
   - 理解 IOC 和 DI 的概念
   - 理解 Bean 的生命周期
   - 理解自动配置的原理

2. **对比学习**
   - BeanFactory vs ApplicationContext
   - Singleton vs Prototype
   - 构造器注入 vs Setter 注入

### 实践学习
1. **动手编码**
   - 运行示例代码
   - 修改代码观察变化
   - 编写测试用例

2. **调试源码**
   - 在关键位置打断点
   - 观察变量的变化
   - 理解执行流程

### 深入学习
1. **阅读源码**
   - AbstractApplicationContext
   - DefaultListableBeanFactory
   - AutoConfigurationImportSelector

2. **扩展实践**
   - 自定义 BeanPostProcessor
   - 自定义 Starter
   - 自定义条件注解

---

## 🎉 总结

本项目整合了 Spring 核心相关的三个学习模块：

1. **spring-annotation** - 深入理解 Spring 注解和 Bean 作用域
2. **spring-ioc** - 掌握 Spring IOC 容器的原理和扩展
3. **springboot-learning** - 学习 Spring Boot 的核心特性

通过系统学习这三个模块，你将全面掌握 Spring 框架的核心知识！

---

**最后更新时间**：2026-01-16

**作者**：huabin

**版本**：v1.0
