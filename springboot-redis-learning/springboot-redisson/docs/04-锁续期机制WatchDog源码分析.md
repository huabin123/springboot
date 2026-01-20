# 04-锁续期机制WatchDog源码分析

## 一、为什么需要锁续期？

### 问题1：固定过期时间的锁有什么问题？

#### 场景重现

```java
RLock lock = redissonClient.getLock("myLock");

// 设置锁30秒后过期
lock.lock(30, TimeUnit.SECONDS);

try {
    // 业务逻辑执行了35秒
    processOrder(); // 耗时35秒
} finally {
    lock.unlock();
}
```

**问题分析**：

```
时刻T0:  客户端A获取锁，设置30秒过期
时刻T30: 锁自动过期（业务还在执行）
时刻T30: 客户端B获取锁成功
时刻T35: 客户端A执行完毕，释放锁（删除了B的锁！）
时刻T35: 客户端C获取锁成功
时刻T40: 客户端B执行完毕，释放锁（删除了C的锁！）
```

**后果**：
- ❌ 锁的互斥性被破坏
- ❌ 多个客户端同时持有锁
- ❌ 数据一致性无法保证

---

### 问题2：如何确定合适的过期时间？

#### 困境

```
过期时间太短：
  ├─ 业务未完成，锁就过期
  └─ 导致并发安全问题

过期时间太长：
  ├─ 客户端崩溃后，锁长时间无法释放
  └─ 影响系统可用性

如何选择？
  └─ 无法准确预估业务执行时间
```

#### 常见错误做法

```java
// ❌ 错误1：设置过短
lock.lock(5, TimeUnit.SECONDS); // 业务可能需要10秒

// ❌ 错误2：设置过长
lock.lock(300, TimeUnit.SECONDS); // 5分钟，太长了

// ❌ 错误3：不设置过期时间（手写Redis命令）
redisTemplate.opsForValue().setIfAbsent("lock", "value"); // 永不过期，死锁风险
```

---

### 问题3：WatchDog 如何解决这个问题？

#### 核心思想

```
不指定过期时间 → 使用默认30秒 → 启动WatchDog定时任务
                                    ↓
                            每10秒检查一次
                                    ↓
                            如果锁还被持有
                                    ↓
                            自动续期到30秒
                                    ↓
                            业务执行完毕
                                    ↓
                            取消定时任务
                                    ↓
                            释放锁
```

**优点**：
- ✅ 业务执行多久，锁就持有多久
- ✅ 不需要预估业务执行时间
- ✅ 客户端崩溃后，锁自动过期（30秒）

---

## 二、WatchDog 工作原理

### 问题4：WatchDog 的完整工作流程是什么？

#### 流程图

```
客户端调用 lock.lock()
         ↓
    未指定过期时间？
    ├─ 是 → leaseTime = -1
    └─ 否 → 使用指定时间，不启动WatchDog
         ↓
    执行加锁 Lua 脚本
    （使用默认30秒过期时间）
         ↓
    加锁成功？
    ├─ 是 → 启动 WatchDog
    │       ↓
    │   创建定时任务
    │   （延迟10秒执行）
    │       ↓
    │   定时任务执行
    │       ↓
    │   检查锁是否还被当前线程持有
    │   ├─ 是 → 执行续期 Lua 脚本
    │   │       （续期到30秒）
    │   │       ↓
    │   │   续期成功？
    │   │   ├─ 是 → 继续调度下一次任务（10秒后）
    │   │   └─ 否 → 停止续期
    │   │
    │   └─ 否 → 停止续期
    │
    └─ 否 → 不启动 WatchDog
         ↓
    执行业务逻辑
         ↓
    调用 lock.unlock()
         ↓
    取消 WatchDog 定时任务
         ↓
    执行解锁 Lua 脚本
```

---

### 问题5：WatchDog 的关键参数是什么？

#### 核心参数

```java
public class Config {
    // 锁的默认过期时间：30秒
    private long lockWatchdogTimeout = 30 * 1000;
    
    // WatchDog 续期间隔：lockWatchdogTimeout / 3 = 10秒
    // 为什么是 1/3？
    // - 确保在锁过期前有足够时间续期
    // - 即使一次续期失败，还有两次机会
}
```

#### 时间线示例

```
T0:   加锁成功，设置30秒过期
T10:  WatchDog第1次续期 → 过期时间延长到 T40
T20:  WatchDog第2次续期 → 过期时间延长到 T50
T30:  WatchDog第3次续期 → 过期时间延长到 T60
T35:  业务执行完毕，unlock()
T35:  取消WatchDog，删除锁
```

---

## 三、WatchDog 源码深度剖析

### 问题6：WatchDog 的启动源码是如何实现的？

#### 加锁时启动 WatchDog

```java
public class RedissonLock extends RedissonExpirable implements RLock {
    
    // WatchDog 定时任务的超时时间
    protected long internalLockLeaseTime;
    
    // 存储每个线程的续期任务
    private static final ConcurrentMap<String, ExpirationEntry> EXPIRATION_RENEWAL_MAP = 
            new ConcurrentHashMap<>();
    
    @Override
    public void lock() {
        try {
            lock(-1, null, false);
        } catch (InterruptedException e) {
            throw new IllegalStateException();
        }
    }
    
    private void lock(long leaseTime, TimeUnit unit, boolean interruptibly) 
            throws InterruptedException {
        
        long threadId = Thread.currentThread().getId();
        Long ttl = tryAcquire(leaseTime, unit, threadId);
        
        // 加锁成功
        if (ttl == null) {
            return;
        }
        
        // 加锁失败，订阅并等待...
        // ...
    }
    
    private Long tryAcquire(long leaseTime, TimeUnit unit, long threadId) {
        return get(tryAcquireAsync(leaseTime, unit, threadId));
    }
    
    private <T> RFuture<Long> tryAcquireAsync(long leaseTime, TimeUnit unit, 
                                               long threadId) {
        // 如果指定了过期时间，直接使用，不启动 WatchDog
        if (leaseTime != -1) {
            return tryLockInnerAsync(leaseTime, unit, threadId, 
                    RedisCommands.EVAL_LONG);
        }
        
        // 未指定过期时间，使用默认值（30秒），并启动 WatchDog
        RFuture<Long> ttlRemainingFuture = tryLockInnerAsync(
                commandExecutor.getConnectionManager().getCfg()
                        .getLockWatchdogTimeout(),  // 默认30秒
                TimeUnit.MILLISECONDS, 
                threadId, 
                RedisCommands.EVAL_LONG);
        
        // 加锁成功后的回调
        ttlRemainingFuture.onComplete((ttlRemaining, e) -> {
            if (e != null) {
                return;
            }
            
            // 加锁成功（ttlRemaining == null）
            if (ttlRemaining == null) {
                // 启动 WatchDog
                scheduleExpirationRenewal(threadId);
            }
        });
        
        return ttlRemainingFuture;
    }
}
```

---

### 问题7：scheduleExpirationRenewal() 如何启动定时任务？

```java
public class RedissonLock extends RedissonExpirable implements RLock {
    
    /**
     * 启动锁续期任务
     */
    private void scheduleExpirationRenewal(long threadId) {
        ExpirationEntry entry = new ExpirationEntry();
        
        // 尝试将续期任务加入 Map
        ExpirationEntry oldEntry = EXPIRATION_RENEWAL_MAP.putIfAbsent(
                getEntryName(), entry);
        
        if (oldEntry != null) {
            // 已经存在续期任务（可重入锁的情况）
            oldEntry.addThreadId(threadId);
        } else {
            // 首次加锁，启动续期任务
            entry.addThreadId(threadId);
            // 开始续期
            renewExpiration();
        }
    }
    
    /**
     * 执行续期逻辑
     */
    private void renewExpiration() {
        ExpirationEntry ee = EXPIRATION_RENEWAL_MAP.get(getEntryName());
        if (ee == null) {
            return;
        }
        
        // 创建定时任务
        Timeout task = commandExecutor.getConnectionManager().newTimeout(
                new TimerTask() {
                    @Override
                    public void run(Timeout timeout) throws Exception {
                        ExpirationEntry ent = EXPIRATION_RENEWAL_MAP.get(getEntryName());
                        if (ent == null) {
                            return;
                        }
                        
                        Long threadId = ent.getFirstThreadId();
                        if (threadId == null) {
                            return;
                        }
                        
                        // 执行续期操作
                        RFuture<Boolean> future = renewExpirationAsync(threadId);
                        
                        future.onComplete((res, e) -> {
                            if (e != null) {
                                log.error("Can't update lock " + getName() + 
                                        " expiration", e);
                                return;
                            }
                            
                            if (res) {
                                // 续期成功，继续调度下一次任务
                                renewExpiration();
                            }
                        });
                    }
                }, 
                // 延迟时间：lockWatchdogTimeout / 3 = 10秒
                internalLockLeaseTime / 3, 
                TimeUnit.MILLISECONDS);
        
        ee.setTimeout(task);
    }
    
    /**
     * 执行续期 Lua 脚本
     */
    protected RFuture<Boolean> renewExpirationAsync(long threadId) {
        return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, 
                RedisCommands.EVAL_BOOLEAN,
                
                // Lua 脚本
                "if (redis.call('hexists', KEYS[1], ARGV[2]) == 1) then " +
                    "redis.call('pexpire', KEYS[1], ARGV[1]); " +
                    "return 1; " +
                "end; " +
                "return 0;",
                
                // 参数
                Collections.singletonList(getName()),  // KEYS[1]: 锁的key
                internalLockLeaseTime,                 // ARGV[1]: 30000ms
                getLockName(threadId));                // ARGV[2]: uuid:threadId
    }
}
```

#### 续期 Lua 脚本解析

```lua
-- KEYS[1]: 锁的key，例如 "myLock"
-- ARGV[1]: 续期时间，例如 30000（毫秒）
-- ARGV[2]: 锁的唯一标识，例如 "uuid:threadId"

-- 判断锁是否还被当前线程持有
if (redis.call('hexists', KEYS[1], ARGV[2]) == 1) then
    -- 是，续期到30秒
    redis.call('pexpire', KEYS[1], ARGV[1]);
    return 1;
end;

-- 否，返回0（锁已被释放或被其他线程持有）
return 0;
```

**为什么要判断 hexists？**

```
场景：
1. 线程A持有锁
2. 线程A执行业务
3. 线程A执行完毕，unlock()
4. WatchDog定时任务触发（延迟执行）
5. 如果不判断，会给已释放的锁续期 → 错误！
```

---

### 问题8：WatchDog 如何取消？

```java
public class RedissonLock extends RedissonExpirable implements RLock {
    
    @Override
    public void unlock() {
        try {
            get(unlockAsync(Thread.currentThread().getId()));
        } catch (RedisException e) {
            // ...
        }
    }
    
    @Override
    public RFuture<Void> unlockAsync(long threadId) {
        RPromise<Void> result = new RedissonPromise<>();
        
        // 执行解锁 Lua 脚本
        RFuture<Boolean> future = unlockInnerAsync(threadId);
        
        future.onComplete((opStatus, e) -> {
            // 取消 WatchDog 续期任务
            cancelExpirationRenewal(threadId);
            
            // ...
        });
        
        return result;
    }
    
    /**
     * 取消续期任务
     */
    void cancelExpirationRenewal(Long threadId) {
        ExpirationEntry task = EXPIRATION_RENEWAL_MAP.get(getEntryName());
        if (task == null) {
            return;
        }
        
        if (threadId != null) {
            task.removeThreadId(threadId);
        }
        
        // 如果没有线程持有锁了，取消定时任务
        if (threadId == null || task.hasNoThreads()) {
            Timeout timeout = task.getTimeout();
            if (timeout != null) {
                timeout.cancel();
            }
            EXPIRATION_RENEWAL_MAP.remove(getEntryName());
        }
    }
}
```

---

### 问题9：ExpirationEntry 的作用是什么？

```java
public class ExpirationEntry {
    
    // 持有锁的线程ID集合（支持可重入）
    private final Map<Long, Integer> threadIds = new LinkedHashMap<>();
    
    // 定时任务
    private volatile Timeout timeout;
    
    /**
     * 添加线程ID
     */
    public synchronized void addThreadId(long threadId) {
        Integer counter = threadIds.get(threadId);
        if (counter == null) {
            counter = 1;
        } else {
            counter++;
        }
        threadIds.put(threadId, counter);
    }
    
    /**
     * 移除线程ID
     */
    public synchronized boolean removeThreadId(long threadId) {
        Integer counter = threadIds.get(threadId);
        if (counter == null) {
            return false;
        }
        
        counter--;
        if (counter == 0) {
            threadIds.remove(threadId);
        } else {
            threadIds.put(threadId, counter);
        }
        
        return true;
    }
    
    /**
     * 判断是否还有线程持有锁
     */
    public boolean hasNoThreads() {
        return threadIds.isEmpty();
    }
    
    /**
     * 获取第一个线程ID（用于续期）
     */
    public Long getFirstThreadId() {
        if (threadIds.isEmpty()) {
            return null;
        }
        return threadIds.keySet().iterator().next();
    }
    
    public void setTimeout(Timeout timeout) {
        this.timeout = timeout;
    }
    
    public Timeout getTimeout() {
        return timeout;
    }
}
```

**为什么需要 ExpirationEntry？**

1. **支持可重入**：记录每个线程的加锁次数
2. **管理定时任务**：存储 Timeout 对象，方便取消
3. **线程安全**：使用 synchronized 保证并发安全

---

## 四、WatchDog 的关键设计

### 问题10：为什么续期间隔是过期时间的 1/3？

#### 设计考虑

```
假设过期时间 = 30秒
续期间隔 = 10秒

时间线：
T0:   加锁，过期时间 = T30
T10:  第1次续期，过期时间 = T40
      ├─ 如果续期失败，还有20秒缓冲
      └─ 可以重试2次（T20, T30）

T20:  第2次续期，过期时间 = T50
      ├─ 如果续期失败，还有10秒缓冲
      └─ 可以重试1次（T30）

T30:  第3次续期，过期时间 = T60
```

**如果是 1/2 呢？**

```
续期间隔 = 15秒

T0:   加锁，过期时间 = T30
T15:  第1次续期，过期时间 = T45
      ├─ 如果续期失败，只有15秒缓冲
      └─ 只能重试1次（T30）

T30:  第2次续期（已经到过期时间了！）
      └─ 如果网络延迟，可能锁已经过期
```

**结论**：1/3 提供了更好的容错性

---

### 问题11：WatchDog 会不会导致锁永远不释放？

#### 场景分析

**场景1：业务代码异常**

```java
RLock lock = redissonClient.getLock("myLock");
lock.lock();

try {
    // 业务逻辑抛出异常
    throw new RuntimeException("业务异常");
} finally {
    lock.unlock(); // finally 保证一定会执行
}
```

**结论**：不会，finally 块保证锁会被释放

---

**场景2：客户端进程崩溃**

```java
RLock lock = redissonClient.getLock("myLock");
lock.lock();

// 进程突然崩溃（kill -9）
System.exit(1);
```

**流程**：

```
T0:   加锁，过期时间 = T30
T5:   进程崩溃
T10:  WatchDog定时任务无法执行（进程已死）
T30:  锁自动过期
T30:  其他客户端可以获取锁
```

**结论**：不会，锁会在30秒后自动过期

---

**场景3：网络分区**

```
客户端A持有锁
    ↓
网络分区（客户端A与Redis断开）
    ↓
WatchDog无法续期
    ↓
锁在30秒后过期
    ↓
客户端B获取锁
    ↓
网络恢复
    ↓
客户端A尝试续期（失败，锁已被B持有）
```

**结论**：不会，网络分区后锁会自动过期

---

### 问题12：WatchDog 的性能开销如何？

#### 资源消耗分析

```
单个锁的开销：
├─ 内存：ExpirationEntry 对象（约100字节）
├─ 定时任务：1个 Timeout 对象
└─ 网络请求：每10秒1次续期请求

1000个锁的开销：
├─ 内存：约100KB
├─ 定时任务：1000个
└─ 网络请求：每秒100次（1000/10）
```

**优化建议**：

1. **避免长时间持有锁**
   ```java
   // ❌ 不推荐
   lock.lock();
   sleep(60000); // 持有锁1分钟
   lock.unlock();
   
   // ✅ 推荐
   lock.lock();
   quickOperation(); // 快速操作
   lock.unlock();
   ```

2. **指定过期时间（不启动WatchDog）**
   ```java
   // 如果能准确预估业务时间
   lock.lock(10, TimeUnit.SECONDS);
   ```

3. **使用 tryLock 避免无限等待**
   ```java
   if (lock.tryLock(5, 10, TimeUnit.SECONDS)) {
       try {
           // 业务逻辑
       } finally {
           lock.unlock();
       }
   }
   ```

---

## 五、WatchDog 的边界情况

### 问题13：可重入锁的 WatchDog 如何处理？

#### 场景

```java
RLock lock = redissonClient.getLock("myLock");

public void methodA() {
    lock.lock(); // 第1次加锁
    try {
        methodB();
    } finally {
        lock.unlock(); // 第1次解锁
    }
}

public void methodB() {
    lock.lock(); // 第2次加锁（重入）
    try {
        // 业务逻辑
    } finally {
        lock.unlock(); // 第2次解锁
    }
}
```

#### WatchDog 处理流程

```
第1次加锁：
├─ 创建 ExpirationEntry
├─ threadIds.put(thread-1, 1)
└─ 启动 WatchDog

第2次加锁（重入）：
├─ ExpirationEntry 已存在
├─ threadIds.put(thread-1, 2)
└─ 不启动新的 WatchDog（复用已有的）

第1次解锁：
├─ threadIds.put(thread-1, 1)
└─ WatchDog 继续运行（还有重入）

第2次解锁：
├─ threadIds.remove(thread-1)
├─ hasNoThreads() == true
└─ 取消 WatchDog
```

**关键点**：
- 只有第一次加锁时启动 WatchDog
- 完全释放锁后才取消 WatchDog
- ExpirationEntry 记录重入次数

---

### 问题14：多线程同时加锁时 WatchDog 如何处理？

#### 场景

```java
RLock lock = redissonClient.getLock("myLock");

// 线程1
new Thread(() -> {
    lock.lock();
    // 持有锁60秒
    sleep(60000);
    lock.unlock();
}).start();

// 线程2（等待线程1释放锁）
new Thread(() -> {
    lock.lock();
    // 持有锁30秒
    sleep(30000);
    lock.unlock();
}).start();
```

#### WatchDog 处理流程

```
T0:   线程1加锁成功
      ├─ ExpirationEntry.threadIds = {thread-1: 1}
      └─ 启动 WatchDog-1

T10:  WatchDog-1 续期（线程1持有）

T20:  WatchDog-1 续期（线程1持有）

T30:  WatchDog-1 续期（线程1持有）

T40:  WatchDog-1 续期（线程1持有）

T50:  WatchDog-1 续期（线程1持有）

T60:  线程1释放锁
      ├─ ExpirationEntry.threadIds = {}
      └─ 取消 WatchDog-1

T60:  线程2加锁成功
      ├─ ExpirationEntry.threadIds = {thread-2: 1}
      └─ 启动 WatchDog-2

T70:  WatchDog-2 续期（线程2持有）

T80:  WatchDog-2 续期（线程2持有）

T90:  线程2释放锁
      ├─ ExpirationEntry.threadIds = {}
      └─ 取消 WatchDog-2
```

**关键点**：
- 每个线程有独立的 WatchDog
- 通过 ExpirationEntry 管理多线程

---

## 六、总结

### 核心要点

1. **WatchDog 解决的问题**
   - 无需预估业务执行时间
   - 避免锁提前过期
   - 防止客户端崩溃导致死锁

2. **WatchDog 的工作机制**
   - 默认30秒过期时间
   - 每10秒续期一次（1/3）
   - 业务完成后自动取消

3. **WatchDog 的启动条件**
   - 调用 `lock()` 不指定过期时间
   - 加锁成功后自动启动

4. **WatchDog 的取消时机**
   - 调用 `unlock()` 时
   - 锁完全释放后（重入次数为0）

5. **WatchDog 的容错性**
   - 客户端崩溃：锁自动过期
   - 网络分区：续期失败，锁过期
   - 业务异常：finally 保证释放

### 最佳实践

```java
// ✅ 推荐：不指定过期时间，启用 WatchDog
RLock lock = redissonClient.getLock("myLock");
lock.lock();
try {
    // 业务逻辑（执行多久都可以）
    processOrder();
} finally {
    lock.unlock();
}

// ⚠️ 谨慎使用：指定过期时间，不启用 WatchDog
lock.lock(30, TimeUnit.SECONDS);
try {
    // 必须在30秒内完成
    quickOperation();
} finally {
    lock.unlock();
}
```

---

## 七、思考题

1. **如果 WatchDog 续期失败会怎样？**
   - 提示：网络抖动、Redis 宕机

2. **为什么不把续期间隔设置为 1 秒？**
   - 提示：性能开销、网络压力

3. **WatchDog 能否防止主从切换时的锁丢失？**
   - 提示：Redis 主从复制的异步性

4. **如果业务代码进入死循环，WatchDog 会一直续期吗？**
   - 提示：是的，如何避免？

---

**下一篇**：[05-可重入锁实现原理.md](./05-可重入锁实现原理.md) - 深入剖析 Hash 结构和重入计数机制
