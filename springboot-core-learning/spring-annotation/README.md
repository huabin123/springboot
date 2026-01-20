# Spring Annotation Learning Project

> Dive into Spring annotations, Bean scopes, and dependency injection

## Documentation Directory

### Key Documents

1. **[01-Spring-Bean作用域与递归隔离完整指南](./docs/01-Spring-Bean作用域与递归隔离完整指南.md)**
   - Spring Bean Scope 详解
   - Singleton vs Prototype vs Request Scope
   - 递归场景下的状态隔离
   - 完整的代码示例和测试验证

2. **[02-递归场景下的状态隔离核心原理](./docs/02-递归场景下的状态隔离核心原理.md)**
   - 问题的本质分析
   - Prototype Scope 的工作原理
   - Request Scope 的状态管理
   - 性能对比和最佳实践

3. **[03-getBean与ObjectProvider详解](./docs/03-getBean与ObjectProvider详解.md)**
   - applicationContext.getBean() 详解
   - ObjectProvider 的使用场景
   - 两者在不同 Scope 下的行为对比
   - 类型安全和空值处理

4. **[04-快速参考指南](./docs/04-快速参考指南.md)**
   - 一句话总结核心概念
   - 常见问题快速解答
   - 代码模板和最佳实践

---

## Learning Objectives

Through this project, you will master:

1. **Spring Bean Scopes**
   - Singleton (单例)
   - Prototype (原型)
   - Request (请求)
   - Session (会话)

2. **Dependency Injection Methods**
   - Constructor Injection
   - Setter Injection
   - Field Injection
   - ObjectProvider-based Injection

3. **Bean Retrieval Methods**
   - ApplicationContext.getBean()
   - ObjectProvider.getObject()
   - @Autowired-based Injection

4. **Recursive Scenario Handling**
   - State Isolation Implementation
   - Snapshot-based Recovery
   - Performance Optimization Strategies

---

## Quick Start

### Environment Requirements

- JDK 1.8+
- Maven 3.6+
- Spring Boot 2.2.5+

### Running the Project

```bash
cd spring-annotation
mvn spring-boot:run
```

---

## Key Concepts

### Bean Scope Comparison

| Scope | 说明 | 创建时机 | 生命周期 | 适用场景 |
|-------|------|----------|----------|----------|
| **Singleton** | 单例 | 容器启动时 | 容器生命周期 | 无状态Bean |
| **Prototype** | 原型 | 每次获取时 | 获取后由调用者管理 | 有状态Bean |
| **Request** | 请求 | 每个HTTP请求 | 请求结束 | Web应用 |
| **Session** | 会话 | 每个HTTP会话 | 会话结束 | Web应用 |

### Recursive Isolation Key

**Core Conclusion**：Recursive Isolation 的关键是 `@Scope("prototype")`，而不是获取方式！

```java
// Correct: Using Prototype Scope
@Scope("prototype")
public class MyService {
    private int counter = 0;
    
    public void process() {
        counter++;
        if (condition) {
            // Each time is a new instance, state is naturally isolated
            MyService newInstance = applicationContext.getBean(MyService.class);
            newInstance.process();
        }
    }
}

// Incorrect: Using Request Scope
@RequestScope
public class MyService {
    private int counter = 0;
    
    public void process() {
        counter++;  // Recursive calls will share the same state
        if (condition) {
            this.process();  // Same instance
        }
    }
}
```

---

## Best Practices

### 1. Choose the Right Scope

- **Stateless Services**：Use `@Singleton` (default)
- **Stateful Services with Isolation**：Use `@Prototype`
- **Web Request-related**：Use `@RequestScope`

### 2. Bean Retrieval Methods

```java
// Method 1: Direct Injection (Recommended)
@Autowired
private MyService myService;

// Method 2: ObjectProvider-based Injection
@Autowired
private ObjectProvider<MyService> myServiceProvider;

// Method 3: ApplicationContext-based Injection (Not Recommended)
@Autowired
private ApplicationContext applicationContext;
MyService service = applicationContext.getBean(MyService.class);
```

### 3. Recursive Scenario Handling

```java
// Solution 1: Prototype Scope (Recommended)
@Scope("prototype")
public class RecursiveService {
    public void process() {
        if (needRecursion) {
            RecursiveService newInstance = provider.getObject();
            newInstance.process();
        }
    }
}

// Solution 2: Request Scope + Snapshot-based Recovery
@RequestScope
public class RecursiveService {
    private State state;
    
    public void process() {
        State snapshot = state.copy();
        try {
            if (needRecursion) {
                this.process();
            }
        } finally {
            state = snapshot;
        }
    }
}
