# Spring Bean Scope 与递归隔离完整指南

## 📋 目录

1. [核心问题](#核心问题)
2. [关键结论](#关键结论)
3. [原理解析](#原理解析)
4. [代码示例](#代码示例)
5. [测试验证](#测试验证)
6. [文档导航](#文档导航)

---

## 核心问题

**你的问题：**
> SpringRequestScopeDemoV3中使用`applicationContext.getBean()`和使用`ObjectProvider`获取类有什么区别？
> 使用`applicationContext.getBean()`能够做到递归下的类变量环境隔离吗？

---

## 关键结论

### 🎯 结论1：获取方式行为一致

**`applicationContext.getBean()` 和 `ObjectProvider.getObject()` 在获取Bean的行为上完全一致！**

| Scope类型 | getBean()行为 | ObjectProvider行为 | 是否一致 |
|----------|--------------|-------------------|---------|
| Prototype | 每次创建新实例 | 每次创建新实例 | ✅ 一致 |
| Request | 同一请求返回同一实例 | 同一请求返回同一实例 | ✅ 一致 |
| Singleton | 始终返回同一实例 | 始终返回同一实例 | ✅ 一致 |

### 🎯 结论2：隔离的关键是Scope

**SpringRequestScopeDemoV3能做到递归隔离的原因是 `@Scope("prototype")`，而不是使用了`getBean()`！**

```java
@Scope("prototype")  // ← 这是关键！
public class SpringRequestScopeDemoV3 {
    // 每次getBean()都创建新实例
    // 新实例有独立的成员变量
    // 因此递归调用时状态天然隔离
}
```

### 🎯 结论3：ObjectProvider的优势在于API

**ObjectProvider不是为了改变获取行为，而是提供更便利的API！**

```java
// getBean() - 基础功能
MyBean bean = ctx.getBean(MyBean.class);

// ObjectProvider - 增强功能
MyBean bean1 = provider.getObject();           // 同getBean()
MyBean bean2 = provider.getIfAvailable();      // 安全获取（不抛异常）
MyBean bean3 = provider.getIfUnique();         // 唯一性检查
provider.stream().forEach(b -> b.process());   // 流式处理
```

---

## 原理解析

### 为什么V3能隔离？

#### 代码结构

```java
@Scope("prototype")  // ← 关键点1
@RestController
public class SpringRequestScopeDemoV3 {
    
    @Autowired
    private ApplicationContext applicationContext;
    
    // 成员变量
    private List<String> listA;
    private List<String> listB;
    private List<String> listAll;
    
    public void methodB(List<String> b) {
        for (String s : b) {
            if (s.contains("rank")) {
                // 关键点2：getBean()因为是prototype，创建新实例
                SpringRequestScopeDemoV3 recursiveProcessor = 
                    applicationContext.getBean(SpringRequestScopeDemoV3.class);
                
                // 关键点3：新实例有独立的listA、listB、listAll
                String result = recursiveProcessor.mainMethod(...);
                
                listB.add(result);
            }
        }
    }
}
```

#### 执行流程

```
HTTP请求: mainMethod(["1"], ["2", "rank:2,3"])
│
├─ Spring创建实例A（prototype scope）
│  │
│  └─ 实例A.mainMethod(["1"], ["2", "rank:2,3"])
│     │
│     ├─ 实例A.listA = ["1"]
│     │
│     ├─ 实例A.methodB(["2", "rank:2,3"])
│     │  │
│     │  ├─ 处理"2" → 实例A.listB = ["2"]
│     │  │
│     │  └─ 处理"rank:2,3"
│     │     │
│     │     ├─ getBean() → Spring创建新实例B ⭐
│     │     │  （实例B有独立的listA、listB、listAll）
│     │     │
│     │     └─ 实例B.mainMethod(["2"], ["3"])
│     │        │
│     │        ├─ 实例B.listA = ["2"]  ← 不影响实例A
│     │        ├─ 实例B.listB = ["3"]  ← 不影响实例A
│     │        │
│     │        └─ 返回 "23"
│     │
│     └─ 实例A.listB = ["2", "23"]  ← 实例A状态未被污染
│
└─ 最终结果: "123"
```

**关键点：**
- 实例A和实例B是完全独立的对象
- 实例B的操作不会影响实例A的成员变量
- 这就是"递归下的类变量环境隔离"

### 如果改成Request Scope会怎样？

```java
@RequestScope  // ← 改成request scope
public class SpringRequestScopeDemoV3 {
    
    public void methodB(List<String> b) {
        // getBean()返回的是同一个实例！
        SpringRequestScopeDemoV3 recursiveProcessor = 
            applicationContext.getBean(SpringRequestScopeDemoV3.class);
        
        // recursiveProcessor == this ❌
        // 递归调用会污染当前实例的状态
    }
}
```

**问题：**
- Request scope保证同一请求内只有一个实例
- 递归调用时`getBean()`返回的是同一个实例
- `initializeState()`会清空外层递归的数据
- 内层递归会污染外层的状态

**解决方案（如SpringRequestScopeDemoV2）：**
- 使用状态快照（StateSnapshot）
- 递归前保存状态，递归后恢复状态

### 如果改用ObjectProvider会怎样？

```java
@Scope("prototype")
public class SpringRequestScopeDemoV3 {
    
    @Autowired
    private ObjectProvider<SpringRequestScopeDemoV3> objectProvider;
    
    public void methodB(List<String> b) {
        // 使用ObjectProvider.getObject()
        SpringRequestScopeDemoV3 recursiveProcessor = 
            objectProvider.getObject();  // ← 同样创建新实例
        
        String result = recursiveProcessor.mainMethod(...);
        listB.add(result);
    }
}
```

**结果：完全一样！同样能隔离！**

因为：
- `@Scope("prototype")` 决定了创建策略
- `objectProvider.getObject()` 和 `getBean()` 都遵循这个策略
- 都会创建新实例，都能隔离

---

## 代码示例

### 示例1：Prototype + getBean()

```java
@Scope("prototype")
@RestController
public class ProcessorWithGetBean {
    
    @Autowired
    private ApplicationContext applicationContext;
    
    private List<String> data = new ArrayList<>();
    
    public String process(List<String> input) {
        data.clear();
        
        for (String item : input) {
            if (needsRecursion(item)) {
                // 创建新实例处理递归
                ProcessorWithGetBean newInstance = 
                    applicationContext.getBean(ProcessorWithGetBean.class);
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

### 示例2：Prototype + ObjectProvider

```java
@Scope("prototype")
@RestController
public class ProcessorWithObjectProvider {
    
    @Autowired
    private ObjectProvider<ProcessorWithObjectProvider> objectProvider;
    
    private List<String> data = new ArrayList<>();
    
    public String process(List<String> input) {
        data.clear();
        
        for (String item : input) {
            if (needsRecursion(item)) {
                // 创建新实例处理递归
                ProcessorWithObjectProvider newInstance = 
                    objectProvider.getObject();
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

**两者效果完全一样！**

### 示例3：Request + 状态快照

```java
@RequestScope
@RestController
public class ProcessorWithSnapshot {
    
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
                    ProcessorWithSnapshot sameInstance = 
                        applicationContext.getBean(ProcessorWithSnapshot.class);
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

## 测试验证

### 启动应用后测试

```bash
# 1. 测试Prototype + getBean()
curl http://localhost:8080/compare/getbean
# 输出: 使用getBean()结果: A,B,[C,D],E,[F,G]

# 2. 测试Prototype + ObjectProvider
curl http://localhost:8080/compare/objectprovider
# 输出: 使用ObjectProvider结果: A,B,[C,D],E,[F,G]

# 3. 验证两者结果一致
curl http://localhost:8080/compare/verify
# 输出: 
# getBean()结果: 1,2,[3,4]
# ObjectProvider结果: 1,2,[3,4]
# 结果是否一致: true ✅

# 4. 测试Request Scope（错误示例）
curl http://localhost:8080/test/request-getbean-wrong
# 输出: 状态污染的错误结果

# 5. 测试Request Scope（正确示例）
curl http://localhost:8080/test/request-getbean-correct
# 输出: 通过状态快照实现的正确结果
```

### 观察日志

```
# Prototype + getBean()
🔵 [getBean] 创建新实例: 123456
🔵 [getBean] 实例123456 开始处理: a=[A], b=[B, rank:C,D]
🔵 [getBean] 实例123456 触发递归: rank:C,D
🔵 [getBean] 创建新实例: 789012  ← 新实例
🔵 [getBean] 实例789012 开始处理: a=[C], b=[D]
🔵 [getBean] 实例789012 完成处理: C,D
🔵 [getBean] 实例123456 递归返回: C,D
🔵 [getBean] 实例123456 完成处理: A,B,[C,D]

# Prototype + ObjectProvider
🟢 [ObjectProvider] 创建新实例: 345678
🟢 [ObjectProvider] 实例345678 开始处理: a=[A], b=[B, rank:C,D]
🟢 [ObjectProvider] 实例345678 触发递归: rank:C,D
🟢 [ObjectProvider] 创建新实例: 901234  ← 新实例
🟢 [ObjectProvider] 实例901234 开始处理: a=[C], b=[D]
🟢 [ObjectProvider] 实例901234 完成处理: C,D
🟢 [ObjectProvider] 实例345678 递归返回: C,D
🟢 [ObjectProvider] 实例345678 完成处理: A,B,[C,D]
```

**观察点：**
- 两种方式都创建了新实例
- 实例ID不同，证明是独立对象
- 递归调用时状态完全隔离

---

## 文档导航

### 📚 详细文档

1. **[QUICK_REFERENCE.md](QUICK_REFERENCE.md)**
   - 快速参考指南
   - 决策树和代码模板
   - 常见误区和最佳实践

2. **[GETBEAN_VS_OBJECTPROVIDER.md](GETBEAN_VS_OBJECTPROVIDER.md)**
   - 详细对比分析
   - 不同Scope下的行为
   - ObjectProvider的优势

3. **[RECURSION_ISOLATION_SUMMARY.md](RECURSION_ISOLATION_SUMMARY.md)**
   - 递归隔离原理图解
   - 完整执行流程分析
   - 性能对比和选择建议

### 💻 代码示例

1. **[SpringRequestScopeDemoV3.java](src/main/java/com/huabin/springannotation/scope/SpringRequestScopeDemoV3.java)**
   - 原始实现（Prototype + getBean()）
   - 递归隔离的正确示例

2. **[ComparisonDemo.java](src/main/java/com/huabin/springannotation/scope/ComparisonDemo.java)**
   - getBean() vs ObjectProvider对比
   - 详细注释说明

3. **[SideBySideComparison.java](src/main/java/com/huabin/springannotation/scope/SideBySideComparison.java)**
   - 并排对比实现
   - 包含测试端点

4. **[RecursionIsolationTest.java](src/main/java/com/huabin/springannotation/scope/RecursionIsolationTest.java)**
   - 完整测试用例
   - 正确和错误示例对比

---

## 总结

### 核心要点

1. **获取方式不影响隔离行为**
   - `getBean()` 和 `ObjectProvider` 行为一致
   - 都遵循Bean的scope策略

2. **隔离的关键是Scope**
   - `@Scope("prototype")` → 每次创建新实例 → 天然隔离
   - `@RequestScope` → 同一请求同一实例 → 需要手动隔离

3. **SpringRequestScopeDemoV3能隔离的原因**
   - 使用了 `@Scope("prototype")`
   - 每次递归调用 `getBean()` 都创建新实例
   - 新实例有独立的成员变量

4. **ObjectProvider的价值**
   - 不是为了改变获取行为
   - 而是提供更便利的API
   - 如：空值处理、流式操作、延迟注入等

### 最终建议

```java
// 递归场景下的状态隔离，两种方式都可以：

// 方式1：使用getBean()
@Scope("prototype")
public class MyProcessor {
    @Autowired
    private ApplicationContext ctx;
    
    void process() {
        MyProcessor newInstance = ctx.getBean(MyProcessor.class);
        newInstance.process();  // 新实例，天然隔离
    }
}

// 方式2：使用ObjectProvider（推荐）
@Scope("prototype")
public class MyProcessor {
    @Autowired
    private ObjectProvider<MyProcessor> provider;
    
    void process() {
        MyProcessor newInstance = provider.getObject();
        newInstance.process();  // 新实例，天然隔离
    }
}

// 两者效果完全一样，选择你喜欢的即可！
// ObjectProvider提供更多便利方法，推荐使用。
```

---

## 常见问题

### Q: 为什么不直接用this递归调用？

```java
// 错误做法
public String process(List<String> input) {
    for (String item : input) {
        if (needsRecursion(item)) {
            String result = this.process(extractData(item));  // ❌
            // 使用同一实例，状态会污染
        }
    }
}

// 正确做法
public String process(List<String> input) {
    for (String item : input) {
        if (needsRecursion(item)) {
            MyProcessor newInstance = provider.getObject();  // ✅
            String result = newInstance.process(extractData(item));
            // 使用新实例，状态隔离
        }
    }
}
```

### Q: Request Scope一定不能用于递归吗？

**A:** 可以用，但需要手动状态管理。

参考 `SpringRequestScopeDemoV2` 的 `StateSnapshot` 实现：
- 递归前保存状态
- 递归后恢复状态
- 适用于性能敏感场景

### Q: 性能上有什么差异？

| 方案 | 实例创建 | 内存占用 | 适用场景 |
|------|---------|---------|---------|
| Prototype | 频繁创建 | 高 | 实例轻量，递归深度小 |
| Request + 快照 | 仅1次 | 中 | 实例重量，递归深度大 |

---

**如有疑问，请参考详细文档或查看代码示例！**
