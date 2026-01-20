# 递归场景下的状态隔离 - 核心原理总结

## 问题的本质

**你的问题核心：** 为什么`SpringRequestScopeDemoV3`使用`applicationContext.getBean()`能做到递归下的类变量环境隔离？

**答案：** 不是因为使用了`getBean()`，而是因为使用了`@Scope("prototype")`

---

## 关键对比表

| 维度 | SpringRequestScopeDemoV3 | SpringRequestScopeDemo/V2 |
|------|-------------------------|---------------------------|
| **Scope类型** | `@Scope("prototype")` | `@RequestScope` |
| **获取Bean方式** | `applicationContext.getBean()` | `this.mainMethod()`直接调用 |
| **递归时的实例** | 每次都是新实例 | 同一个实例 |
| **状态隔离** | ✅ 天然隔离（不同实例） | ❌ 需要手动快照恢复 |
| **性能** | 较低（频繁创建实例） | 较高（复用实例） |

---

## 核心原理图解

### 场景1：Prototype Scope（V3的做法）

```
HTTP请求到达
    │
    ├─ Spring容器创建实例A（因为Controller需要处理请求）
    │   │
    │   └─ 实例A.mainMethod(["1"], ["2", "rank:2,3"])
    │       │
    │       ├─ 实例A的成员变量：
    │       │   listA = []
    │       │   listB = []
    │       │   listAll = []
    │       │
    │       ├─ initializeState() → listA=[], listB=[], listAll=[]
    │       ├─ methodA(["1"]) → listA=["1"]
    │       │
    │       ├─ methodB(["2", "rank:2,3"])
    │       │   ├─ 处理"2" → listB=["2"]
    │       │   │
    │       │   └─ 处理"rank:2,3"
    │       │       │
    │       │       ├─ getBean() → Spring创建新实例B ⭐
    │       │       │   │
    │       │       │   └─ 实例B的成员变量（独立内存空间）：
    │       │       │       listA = []  ← 与实例A完全独立
    │       │       │       listB = []
    │       │       │       listAll = []
    │       │       │
    │       │       └─ 实例B.mainMethod(["2"], ["3"])
    │       │           ├─ initializeState()
    │       │           ├─ methodA(["2"]) → 实例B.listA=["2"]
    │       │           ├─ methodB(["3"]) → 实例B.listB=["3"]
    │       │           └─ 返回"2"
    │       │
    │       └─ 实例A.listB=["2", "2"]  ← 实例A的状态未受影响
```

**关键点：**
- `getBean(SpringRequestScopeDemoV3.class)` 因为是prototype，所以创建了新实例B
- 实例B有自己独立的`listA`, `listB`, `listAll`
- 实例B的操作不会影响实例A

---

### 场景2：Request Scope（V1/V2的问题）

```
HTTP请求到达
    │
    ├─ Spring容器创建实例A（request scope，整个请求期间唯一）
    │   │
    │   └─ 实例A.mainMethod(["1"], ["2", "rank:2,3"])
    │       │
    │       ├─ 实例A的成员变量：
    │       │   listA = []
    │       │   listB = []
    │       │   listAll = []
    │       │
    │       ├─ methodA(["1"]) → listA=["1"]
    │       │
    │       ├─ methodB(["2", "rank:2,3"])
    │       │   ├─ 处理"2" → listB=["2"]
    │       │   │
    │       │   └─ 处理"rank:2,3"
    │       │       │
    │       │       ├─ this.mainMethod() 或 getBean() → 返回同一实例A ⚠️
    │       │       │   （request scope特性：同一请求返回同一实例）
    │       │       │
    │       │       └─ 实例A.mainMethod(["2"], ["3"])  ← 递归调用自己
    │       │           ├─ methodA(["2"]) → listA=["1","2"] ❌ 污染了外层
    │       │           ├─ methodB(["3"]) → listB=["2","3"] ❌ 污染了外层
    │       │           └─ methodC() → listAll被污染
    │       │
    │       └─ 外层的listA、listB已被内层污染 ❌
```

**问题：**
- Request scope保证同一请求内只有一个实例
- 递归调用时获取的是同一个实例A
- 内层递归会修改外层的成员变量

---

## getBean() vs ObjectProvider 的真相

### 实验验证

```java
// 实验1：Prototype Scope
@Scope("prototype")
class MyBean {
    @Autowired
    private ApplicationContext ctx;
    
    @Autowired
    private ObjectProvider<MyBean> provider;
    
    void test() {
        MyBean bean1 = ctx.getBean(MyBean.class);
        MyBean bean2 = provider.getObject();
        
        System.out.println(bean1 == bean2);  // false
        System.out.println(bean1 == this);   // false
        System.out.println(bean2 == this);   // false
        
        // 结论：两种方式都创建新实例，行为一致
    }
}

// 实验2：Request Scope
@RequestScope
class MyBean {
    @Autowired
    private ApplicationContext ctx;
    
    @Autowired
    private ObjectProvider<MyBean> provider;
    
    void test() {
        MyBean bean1 = ctx.getBean(MyBean.class);
        MyBean bean2 = provider.getObject();
        
        System.out.println(bean1 == bean2);  // true
        System.out.println(bean1 == this);   // true
        System.out.println(bean2 == this);   // true
        
        // 结论：两种方式都返回同一实例，行为一致
    }
}
```

### 结论

**`getBean()` 和 `ObjectProvider.getObject()` 在获取Bean的行为上完全一致！**

- 都遵循Bean的scope策略
- Prototype → 每次创建新实例
- Request → 同一请求返回同一实例
- Singleton → 始终返回同一实例

**区别仅在于API的便利性：**

```java
// getBean() - 基础API
MyBean bean = ctx.getBean(MyBean.class);

// ObjectProvider - 增强API
MyBean bean1 = provider.getObject();           // 同getBean()
MyBean bean2 = provider.getIfAvailable();      // 找不到返回null而不是异常
MyBean bean3 = provider.getIfUnique();         // 多个Bean时抛异常
provider.stream().forEach(b -> b.process());   // 流式处理
```

---

## 为什么V3能隔离？完整解析

### 代码分析

```java
@Scope("prototype")  // ← 关键1：prototype scope
@RestController
public class SpringRequestScopeDemoV3 {
    
    @Autowired
    private ApplicationContext applicationContext;
    
    private List<String> listA;  // 成员变量
    private List<String> listB;
    private List<String> listAll;
    
    public void methodB(List<String> b) {
        for (String s : b) {
            if (s.contains("rank")) {
                // 关键2：getBean()因为是prototype，所以创建新实例
                SpringRequestScopeDemoV3 recursiveProcessor = 
                    applicationContext.getBean(SpringRequestScopeDemoV3.class);
                
                // 关键3：新实例有独立的listA、listB、listAll
                String result = recursiveProcessor.mainMethod(...);
                
                listB.add(result);  // 当前实例的listB
            }
        }
    }
}
```

### 三个关键点

1. **`@Scope("prototype")`** - 告诉Spring每次getBean()都创建新实例
2. **`applicationContext.getBean()`** - 触发Spring创建新实例
3. **成员变量独立** - 每个实例有自己的内存空间

### 如果改成ObjectProvider会怎样？

```java
@Scope("prototype")
@RestController
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

## 实战建议

### 选择Scope的考虑

| 场景 | 推荐Scope | 原因 |
|------|----------|------|
| 递归调用，需要状态隔离 | Prototype | 天然隔离，无需手动管理 |
| 递归调用，性能敏感 | Request + 状态快照 | 复用实例，减少创建开销 |
| 无递归，简单请求处理 | Request | 最简单，性能最好 |
| 无状态服务 | Singleton | 最高性能 |

### 选择获取方式的考虑

| 场景 | 推荐方式 | 原因 |
|------|---------|------|
| 简单获取Bean | `getBean()` | 直接明了 |
| 需要空值处理 | `ObjectProvider.getIfAvailable()` | 避免异常 |
| 需要处理多个Bean | `ObjectProvider.stream()` | 流式处理 |
| 避免循环依赖 | `ObjectProvider` 注入 | 延迟获取 |
| 需要条件获取 | `ObjectProvider.getIfUnique()` | 安全获取 |

### 性能对比

```java
// 假设递归深度为10

// 方案1：Prototype Scope
// - 创建11个实例（1个顶层 + 10个递归）
// - 内存占用：11 * 实例大小
// - 无状态管理开销

// 方案2：Request Scope + 状态快照
// - 创建1个实例
// - 内存占用：1 * 实例大小 + 10 * 快照大小
// - 有状态保存/恢复开销

// 选择建议：
// - 实例创建成本高 → Request Scope
// - 状态复杂度高 → Prototype Scope
```

---

## 总结

### 核心要点

1. **状态隔离的关键是Scope，不是获取方式**
   - `@Scope("prototype")` → 每次创建新实例 → 天然隔离
   - `@RequestScope` → 同一请求同一实例 → 需要手动隔离

2. **getBean()和ObjectProvider行为一致**
   - 都遵循Bean的scope策略
   - 区别仅在于API便利性

3. **SpringRequestScopeDemoV3能隔离的原因**
   - 使用了`@Scope("prototype")`
   - 每次递归调用`getBean()`都创建新实例
   - 新实例有独立的成员变量

4. **如果改用ObjectProvider**
   - 同样能实现隔离
   - 行为完全一致
   - 只是API更方便

### 最终建议

```java
// 如果你的场景是递归调用需要状态隔离

// 方案1：简单直接（推荐）
@Scope("prototype")
class MyProcessor {
    @Autowired
    private ApplicationContext ctx;
    
    void process() {
        MyProcessor newInstance = ctx.getBean(MyProcessor.class);
        newInstance.process();  // 新实例，天然隔离
    }
}

// 方案2：API更优雅（同样推荐）
@Scope("prototype")
class MyProcessor {
    @Autowired
    private ObjectProvider<MyProcessor> provider;
    
    void process() {
        MyProcessor newInstance = provider.getObject();
        newInstance.process();  // 新实例，天然隔离
    }
}

// 两者效果完全一样，选择你喜欢的即可！
```
