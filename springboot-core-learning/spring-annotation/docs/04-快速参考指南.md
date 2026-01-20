# 快速参考指南：递归隔离与Bean获取

## 一句话总结

**`applicationContext.getBean()` 和 `ObjectProvider.getObject()` 在获取Bean的行为上完全一致，递归隔离的关键是 `@Scope("prototype")` 而不是获取方式。**

---

## 核心问题解答

### Q1: SpringRequestScopeDemoV3为什么能做到递归隔离？

**A:** 因为使用了 `@Scope("prototype")`，每次 `getBean()` 都创建新实例。

```java
@Scope("prototype")  // ← 这是关键
public class SpringRequestScopeDemoV3 {
    SpringRequestScopeDemoV3 newInstance = 
        applicationContext.getBean(SpringRequestScopeDemoV3.class);
    // 每次都是新实例，有独立的成员变量
}
```

### Q2: 使用ObjectProvider能达到同样效果吗？

**A:** 能！完全一样！

```java
@Scope("prototype")
public class MyProcessor {
    @Autowired
    private ObjectProvider<MyProcessor> provider;
    
    void process() {
        MyProcessor newInstance = provider.getObject();
        // 同样创建新实例，同样隔离
    }
}
```

### Q3: getBean()和ObjectProvider有什么区别？

**A:** 获取行为一致，但ObjectProvider提供更多便利方法。

```java
// getBean() - 基础API
MyBean bean = ctx.getBean(MyBean.class);

// ObjectProvider - 增强API
MyBean bean1 = provider.getObject();          // 同getBean()
MyBean bean2 = provider.getIfAvailable();     // 安全获取
MyBean bean3 = provider.getIfUnique();        // 唯一性检查
provider.stream().forEach(b -> b.process());  // 流式处理
```

---

## 快速决策树

```
需要递归调用且状态隔离？
    │
    ├─ 是 → 选择Scope
    │   │
    │   ├─ 性能不敏感 → @Scope("prototype")
    │   │   └─ 每次创建新实例，天然隔离
    │   │
    │   └─ 性能敏感 → @RequestScope + 状态快照
    │       └─ 复用实例，手动管理状态
    │
    └─ 否 → 使用 @RequestScope 或 @Singleton
        └─ 最简单，性能最好
```

```
选择获取方式？
    │
    ├─ 简单获取 → applicationContext.getBean()
    │
    ├─ 需要空值处理 → ObjectProvider.getIfAvailable()
    │
    ├─ 需要流式处理 → ObjectProvider.stream()
    │
    └─ 避免循环依赖 → 注入ObjectProvider
```

---

## 代码模板

### 模板1：Prototype + getBean()（推荐用于递归隔离）

```java
@Scope("prototype")
@RestController
public class MyProcessor {
    
    @Autowired
    private ApplicationContext applicationContext;
    
    private List<String> data = new ArrayList<>();
    
    private void initializeState() {
        this.data = new ArrayList<>();
    }
    
    public String process(List<String> input) {
        initializeState();
        
        for (String item : input) {
            if (needsRecursion(item)) {
                // 创建新实例处理递归
                MyProcessor newInstance = 
                    applicationContext.getBean(MyProcessor.class);
                String result = newInstance.process(extractData(item));
                data.add(result);
            } else {
                data.add(item);
            }
        }
        
        return String.join(",", data);
    }
}
```

### 模板2：Prototype + ObjectProvider（推荐用于递归隔离）

```java
@Scope("prototype")
@RestController
public class MyProcessor {
    
    @Autowired
    private ObjectProvider<MyProcessor> objectProvider;
    
    private List<String> data = new ArrayList<>();
    
    private void initializeState() {
        this.data = new ArrayList<>();
    }
    
    public String process(List<String> input) {
        initializeState();
        
        for (String item : input) {
            if (needsRecursion(item)) {
                // 创建新实例处理递归
                MyProcessor newInstance = objectProvider.getObject();
                String result = newInstance.process(extractData(item));
                data.add(result);
            } else {
                data.add(item);
            }
        }
        
        return String.join(",", data);
    }
}
```

### 模板3：Request + 状态快照（性能优化）

```java
@RequestScope
@RestController
public class MyProcessor {
    
    @Autowired
    private ApplicationContext applicationContext;
    
    private List<String> data = new ArrayList<>();
    private int recursionDepth = 0;
    
    public String process(List<String> input) {
        recursionDepth++;
        
        // 保存当前状态
        List<String> savedData = new ArrayList<>(data);
        
        try {
            data.clear();
            
            for (String item : input) {
                if (needsRecursion(item)) {
                    // 获取同一实例（request scope）
                    MyProcessor sameInstance = 
                        applicationContext.getBean(MyProcessor.class);
                    String result = sameInstance.process(extractData(item));
                    data.add(result);
                } else {
                    data.add(item);
                }
            }
            
            return String.join(",", data);
        } finally {
            // 恢复状态（仅内层递归需要）
            if (recursionDepth > 1) {
                data = savedData;
            }
            recursionDepth--;
        }
    }
}
```

---

## 常见误区

### ❌ 误区1：认为getBean()和ObjectProvider行为不同

```java
// 错误认知
"getBean()每次创建新实例，ObjectProvider返回同一实例"

// 正确认知
"两者行为完全一致，都遵循Bean的scope策略"
```

### ❌ 误区2：认为使用getBean()就能隔离

```java
// 错误示例
@RequestScope  // ← 问题在这里
public class MyProcessor {
    void process() {
        MyProcessor instance = ctx.getBean(MyProcessor.class);
        // instance == this，无法隔离！
    }
}

// 正确示例
@Scope("prototype")  // ← 关键是scope
public class MyProcessor {
    void process() {
        MyProcessor instance = ctx.getBean(MyProcessor.class);
        // instance != this，成功隔离！
    }
}
```

### ❌ 误区3：认为Request Scope无法处理递归

```java
// 错误认知
"Request Scope不能用于递归场景"

// 正确认知
"Request Scope可以用，但需要手动状态管理"
// 参考SpringRequestScopeDemoV2的StateSnapshot实现
```

---

## 性能对比

| 方案 | 实例创建次数 | 内存占用 | 状态管理复杂度 | 适用场景 |
|------|------------|---------|--------------|---------|
| Prototype | 每次递归创建 | 高 | 低（无需管理） | 递归深度小，实例轻量 |
| Request + 快照 | 仅1次 | 中 | 高（需要快照） | 递归深度大，实例重量 |

**示例计算（递归深度=10）：**

```
Prototype方案：
- 实例数：11个（1个顶层 + 10个递归）
- 内存：11 × 实例大小
- CPU：11 × 初始化成本

Request + 快照方案：
- 实例数：1个
- 内存：1 × 实例大小 + 10 × 快照大小
- CPU：1 × 初始化成本 + 10 × (快照保存 + 恢复)
```

---

## 测试验证

### 测试用例1：验证行为一致性

```bash
# 测试getBean()
curl http://localhost:8080/compare/getbean

# 测试ObjectProvider
curl http://localhost:8080/compare/objectprovider

# 验证结果一致
curl http://localhost:8080/compare/verify
```

### 测试用例2：验证隔离效果

```bash
# Prototype + getBean（应该隔离）
curl http://localhost:8080/test/prototype-getbean

# Prototype + ObjectProvider（应该隔离）
curl http://localhost:8080/test/prototype-objectprovider

# Request + getBean（应该污染）
curl http://localhost:8080/test/request-getbean-wrong

# Request + getBean + 快照（应该隔离）
curl http://localhost:8080/test/request-getbean-correct
```

---

## 最佳实践

### ✅ 推荐做法

1. **递归场景优先使用Prototype Scope**
   ```java
   @Scope("prototype")
   public class RecursiveProcessor {
       // 简单直接，无需状态管理
   }
   ```

2. **选择你喜欢的获取方式**
   ```java
   // 两者都可以，效果一样
   MyBean bean1 = ctx.getBean(MyBean.class);
   MyBean bean2 = provider.getObject();
   ```

3. **需要额外功能时使用ObjectProvider**
   ```java
   // 空值处理
   MyBean bean = provider.getIfAvailable();
   
   // 流式处理
   provider.stream().forEach(b -> b.process());
   ```

### ⚠️ 注意事项

1. **不要混淆Scope和获取方式**
   - Scope决定实例创建策略
   - 获取方式只是API差异

2. **Request Scope递归需要状态管理**
   - 保存状态 → 递归调用 → 恢复状态
   - 参考SpringRequestScopeDemoV2

3. **性能敏感场景权衡选择**
   - 实例轻量 → Prototype
   - 实例重量 → Request + 快照

---

## 相关文件

- **详细对比**: [GETBEAN_VS_OBJECTPROVIDER.md](GETBEAN_VS_OBJECTPROVIDER.md)
- **原理总结**: [RECURSION_ISOLATION_SUMMARY.md](RECURSION_ISOLATION_SUMMARY.md)
- **代码示例**: 
  - [ComparisonDemo.java](src/main/java/com/huabin/springannotation/scope/ComparisonDemo.java)
  - [SideBySideComparison.java](src/main/java/com/huabin/springannotation/scope/SideBySideComparison.java)
  - [RecursionIsolationTest.java](src/main/java/com/huabin/springannotation/scope/RecursionIsolationTest.java)
