# applicationContext.getBean() vs ObjectProvider 详解

## 核心区别总结

### 1. **基本行为差异**

| 特性 | applicationContext.getBean() | ObjectProvider |
|------|------------------------------|----------------|
| 获取方式 | 直接从容器获取Bean | 延迟获取的提供者模式 |
| 类型安全 | 需要强制类型转换（除非使用泛型重载） | 完全类型安全 |
| 空值处理 | 找不到Bean时抛出异常 | 提供getIfAvailable()等安全方法 |
| 依赖注入 | 不支持 | 可以直接注入 |
| 便利方法 | 无 | stream(), forEach(), getIfUnique()等 |

### 2. **在不同Scope下的行为**

#### Prototype Scope（如SpringRequestScopeDemoV3）

```java
@Scope("prototype")
public class SpringRequestScopeDemoV3 {
    // 使用getBean()
    ComparisonDemo instance1 = applicationContext.getBean(ComparisonDemo.class);
    ComparisonDemo instance2 = applicationContext.getBean(ComparisonDemo.class);
    // instance1 != instance2 ✅ 每次都是新实例
    
    // 使用ObjectProvider
    ComparisonDemo instance3 = objectProvider.getObject();
    ComparisonDemo instance4 = objectProvider.getObject();
    // instance3 != instance4 ✅ 每次都是新实例
}
```

**结论：对于prototype scope，两者行为完全一致，都会创建新实例**

#### Request Scope（如SpringRequestScopeDemo）

```java
@RequestScope
public class SpringRequestScopeDemo {
    // 使用getBean()
    ComparisonDemo instance1 = applicationContext.getBean(ComparisonDemo.class);
    ComparisonDemo instance2 = applicationContext.getBean(ComparisonDemo.class);
    // instance1 == instance2 ✅ 同一请求内返回同一实例
    
    // 使用ObjectProvider
    ComparisonDemo instance3 = objectProvider.getObject();
    ComparisonDemo instance4 = objectProvider.getObject();
    // instance3 == instance4 ✅ 同一请求内返回同一实例
}
```

**结论：对于request scope，两者行为也完全一致，都返回同一实例**

---

## 为什么SpringRequestScopeDemoV3能做到递归下的类变量环境隔离？

### 关键因素：**@Scope("prototype")**

```java
@Scope("prototype")  // ← 这是关键！
@RestController
public class SpringRequestScopeDemoV3 {
    private List<String> listA;
    private List<String> listB;
    private List<String> listAll;
    
    public void methodB(List<String> b) {
        // 每次递归调用getBean()都创建新实例
        SpringRequestScopeDemoV3 recursiveProcessor = 
            applicationContext.getBean(SpringRequestScopeDemoV3.class);
        
        // recursiveProcessor有自己独立的listA, listB, listAll
        String result = recursiveProcessor.mainMethod(...);
    }
}
```

### 执行流程分析

假设调用：`mainMethod(["1"], ["2", "rank:2,3"])`

```
请求开始
├─ Spring创建SpringRequestScopeDemoV3实例A（因为是prototype）
│  └─ 实例A.mainMethod(["1"], ["2", "rank:2,3"])
│     ├─ 实例A.initializeState() → listA=[], listB=[], listAll=[]
│     ├─ 实例A.methodA(["1"]) → listA=["1"]
│     ├─ 实例A.methodB(["2", "rank:2,3"])
│     │  ├─ 处理"2" → listB=["2"]
│     │  ├─ 处理"rank:2,3"
│     │  │  ├─ getBean()创建新实例B（独立的内存空间）
│     │  │  └─ 实例B.mainMethod(["2"], ["3"])
│     │  │     ├─ 实例B.initializeState() → listA=[], listB=[], listAll=[]
│     │  │     ├─ 实例B.methodA(["2"]) → 实例B.listA=["2"]
│     │  │     ├─ 实例B.methodB(["3"]) → 实例B.listB=["3"]
│     │  │     ├─ 实例B.methodAddA() → 实例B.listAll=["2"]
│     │  │     └─ 返回"2"
│     │  └─ 实例A.listB=["2", "2"]  ← 实例A的状态未被污染
│     ├─ 实例A.methodAddA() → listAll=["1"]
│     └─ 返回"12"
```

**关键点：**
1. 实例A和实例B是完全独立的对象，有各自的成员变量
2. 实例B的操作不会影响实例A的listA、listB、listAll
3. 递归返回后，实例A的状态保持不变

---

## 如果改用@RequestScope会怎样？

```java
@RequestScope  // ← 改成request scope
@RestController
public class SpringRequestScopeDemoV3 {
    // 同样的代码...
    
    public void methodB(List<String> b) {
        // getBean()返回的是同一个实例！
        SpringRequestScopeDemoV3 recursiveProcessor = 
            applicationContext.getBean(SpringRequestScopeDemoV3.class);
        
        // recursiveProcessor == this ❌
        // 递归调用会污染当前实例的状态
    }
}
```

### 执行流程（错误示例）

```
请求开始
├─ Spring创建SpringRequestScopeDemoV3实例A（request scope）
│  └─ 实例A.mainMethod(["1"], ["2", "rank:2,3"])
│     ├─ 实例A.initializeState() → listA=[], listB=[], listAll=[]
│     ├─ 实例A.methodA(["1"]) → listA=["1"]
│     ├─ 实例A.methodB(["2", "rank:2,3"])
│     │  ├─ 处理"2" → listB=["2"]
│     │  ├─ 处理"rank:2,3"
│     │  │  ├─ getBean()返回同一实例A（request scope特性）
│     │  │  └─ 实例A.mainMethod(["2"], ["3"])  ← 递归调用自己
│     │  │     ├─ 实例A.initializeState() → listA=[], listB=[], listAll=[] ❌ 清空了外层数据
│     │  │     ├─ 实例A.methodA(["2"]) → listA=["2"]
│     │  │     ├─ 实例A.methodB(["3"]) → listB=["3"]
│     │  │     ├─ 实例A.methodAddA() → listAll=["2"]
│     │  │     └─ 返回"2"
│     │  └─ 外层的listA已被清空！❌
│     └─ 最终结果错误
```

**问题：**
1. 递归调用时获取的是同一个实例
2. `initializeState()`会清空外层递归的数据
3. 内层递归会污染外层的状态

**解决方案（如SpringRequestScopeDemoV2）：**
- 使用状态快照（StateSnapshot）保存和恢复状态
- 在递归前保存状态，递归后恢复

---

## ObjectProvider的优势

虽然在获取Bean的行为上与`getBean()`一致，但ObjectProvider提供了更多便利：

### 1. **类型安全**

```java
// getBean() - 需要类型转换
MyBean bean1 = (MyBean) applicationContext.getBean("myBean");
MyBean bean2 = applicationContext.getBean(MyBean.class);

// ObjectProvider - 完全类型安全
@Autowired
private ObjectProvider<MyBean> provider;
MyBean bean3 = provider.getObject();
```

### 2. **安全的空值处理**

```java
// getBean() - 找不到Bean会抛出异常
try {
    MyBean bean = applicationContext.getBean(MyBean.class);
} catch (NoSuchBeanDefinitionException e) {
    // 处理异常
}

// ObjectProvider - 优雅处理
MyBean bean = provider.getIfAvailable(); // 返回null而不是抛异常
MyBean bean = provider.getIfAvailable(() -> new MyBean()); // 提供默认值
```

### 3. **流式操作**

```java
// 处理所有匹配的Bean
objectProvider.stream()
    .filter(bean -> bean.isActive())
    .forEach(bean -> bean.process());

// 获取唯一Bean（多个时抛异常）
MyBean uniqueBean = provider.getIfUnique();
```

### 4. **延迟注入**

```java
@Service
public class MyService {
    // 避免循环依赖
    @Autowired
    private ObjectProvider<AnotherService> anotherServiceProvider;
    
    public void doSomething() {
        // 使用时才获取
        AnotherService service = anotherServiceProvider.getObject();
        service.process();
    }
}
```

---

## 最佳实践建议

### 1. **递归场景下的状态隔离**

**推荐方案A：使用prototype scope + getBean()**
```java
@Scope("prototype")
public class MyProcessor {
    // 每次递归创建新实例，天然隔离
    MyProcessor newInstance = applicationContext.getBean(MyProcessor.class);
}
```

**推荐方案B：使用request scope + 状态快照**
```java
@RequestScope
public class MyProcessor {
    private StateSnapshot snapshot;
    
    public String process() {
        snapshot = new StateSnapshot(this);
        try {
            // 递归处理
            return doProcess();
        } finally {
            if (recursionDepth > 1) {
                snapshot.restore(this);
            }
        }
    }
}
```

### 2. **选择getBean()还是ObjectProvider？**

**使用getBean()的场景：**
- 简单的Bean获取
- 不需要空值处理
- 不需要流式操作

**使用ObjectProvider的场景：**
- 需要延迟注入
- 需要安全的空值处理
- 需要处理多个Bean实例
- 需要避免循环依赖
- 追求更好的类型安全

### 3. **性能考虑**

```java
// prototype scope - 每次创建新实例，有性能开销
@Scope("prototype")
public class HeavyProcessor {
    // 如果递归深度大，会创建大量实例
}

// request scope - 同一请求复用实例，性能更好
@RequestScope
public class LightProcessor {
    // 但需要手动管理状态隔离
}
```

---

## 总结

1. **applicationContext.getBean()和ObjectProvider在获取Bean的行为上完全一致**
   - 都遵循Bean的scope策略
   - prototype每次创建新实例，request同一请求返回同一实例

2. **SpringRequestScopeDemoV3能做到递归隔离的原因是@Scope("prototype")**
   - 与使用getBean()还是ObjectProvider无关
   - 关键在于每次递归都创建了新的实例

3. **ObjectProvider的优势在于提供了更多便利方法**
   - 类型安全
   - 空值处理
   - 流式操作
   - 延迟注入

4. **选择建议**
   - 简单场景：使用getBean()
   - 复杂场景：使用ObjectProvider
   - 递归隔离：使用prototype scope或手动状态管理
