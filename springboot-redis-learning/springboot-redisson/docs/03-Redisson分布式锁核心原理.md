# 03-Redisson分布式锁核心原理

## 一、Redisson 架构概览

### 问题1：Redisson 的整体架构是什么样的？

```
┌─────────────────────────────────────────────────────────┐
│                    应用层 (Application)                   │
│  RLock, RReadWriteLock, RSemaphore, RCountDownLatch...  │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────┴────────────────────────────────────┐
│                  Redisson 核心层                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │  锁管理器     │  │  WatchDog    │  │  Lua脚本     │  │
│  │ LockManager  │  │  自动续期     │  │  原子操作     │  │
│  └──────────────┘  └──────────────┘  └──────────────┘  │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────┴────────────────────────────────────┐
│                  Netty 通信层                             │
│         异步非阻塞 I/O、连接池管理、编解码                 │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────┴────────────────────────────────────┐
│                    Redis 服务器                           │
│         单机、主从、哨兵、集群                             │
└─────────────────────────────────────────────────────────┘
```

**核心组件**：

1. **RLock 接口**：分布式锁的统一抽象
2. **RedissonLock**：基于 Redis 的锁实现
3. **CommandExecutor**：命令执行器，负责与 Redis 通信
4. **LockPubSub**：发布订阅机制，用于锁释放通知
5. **WatchDog**：看门狗机制，自动续期

---

## 二、加锁流程详解

### 问题2：Redisson 加锁的完整流程是什么？

#### 流程图

```
客户端调用 lock.lock()
         ↓
    是否指定过期时间？
    ├─ 是 → leaseTime = 指定时间
    └─ 否 → leaseTime = -1（启用WatchDog）
         ↓
    执行 Lua 加锁脚本
         ↓
    判断返回值
    ├─ nil → 加锁成功
    │         ↓
    │    启动 WatchDog（如果leaseTime=-1）
    │         ↓
    │    返回成功
    │
    └─ 数字 → 加锁失败（返回锁剩余过期时间）
              ↓
         订阅锁释放事件
              ↓
         阻塞等待信号
              ↓
         收到释放信号 or 超时
              ↓
         重新尝试加锁（循环）
```

---

### 问题3：加锁的 Lua 脚本是如何实现的？

#### 核心 Lua 脚本（简化版）

```lua
-- KEYS[1]: 锁的key，例如 "myLock"
-- ARGV[1]: 锁的过期时间（毫秒），例如 30000
-- ARGV[2]: 锁的唯一标识，例如 "uuid:threadId"

-- 判断锁是否存在
if (redis.call('exists', KEYS[1]) == 0) then
    -- 锁不存在，直接加锁
    redis.call('hincrby', KEYS[1], ARGV[2], 1);
    redis.call('pexpire', KEYS[1], ARGV[1]);
    return nil;
end;

-- 锁存在，判断是否是当前线程持有（可重入）
if (redis.call('hexists', KEYS[1], ARGV[2]) == 1) then
    -- 是当前线程，重入次数+1
    redis.call('hincrby', KEYS[1], ARGV[2], 1);
    redis.call('pexpire', KEYS[1], ARGV[1]);
    return nil;
end;

-- 锁被其他线程持有，返回锁的剩余过期时间
return redis.call('pttl', KEYS[1]);
```

#### 脚本解析

**为什么使用 Hash 结构？**

```
Redis 数据结构：
myLock (Hash)
  ├─ uuid:threadId-1 → 2  (重入次数)
  └─ uuid:threadId-2 → 1
```

**优点**：
1. 支持可重入（记录重入次数）
2. 支持多线程（不同线程有不同的field）
3. 原子操作（Lua脚本保证）

---

### 问题4：为什么必须使用 Lua 脚本？

#### 对比：不使用 Lua 脚本的问题

```java
// ❌ 错误示范：多条命令不是原子的
public boolean lock(String key, String value) {
    // 步骤1：判断锁是否存在
    Boolean exists = redisTemplate.hasKey(key);
    
    if (!exists) {
        // 步骤2：设置锁
        redisTemplate.opsForValue().set(key, value, 30, TimeUnit.SECONDS);
        return true;
    }
    
    // 步骤3：判断是否是当前线程
    String currentValue = redisTemplate.opsForValue().get(key);
    if (value.equals(currentValue)) {
        // 步骤4：重入次数+1
        // ...
    }
    
    return false;
}
```

**问题**：

```
时刻T1: 线程A判断锁不存在
时刻T2: 线程B判断锁不存在
时刻T3: 线程A设置锁
时刻T4: 线程B设置锁（覆盖了A的锁）
结果: 两个线程都认为自己获取了锁！
```

#### 使用 Lua 脚本的优势

1. **原子性**：Lua 脚本在 Redis 中是原子执行的
2. **减少网络开销**：多条命令一次性发送
3. **避免竞态条件**：不会被其他命令打断

---

### 问题5：加锁的 Java 源码是如何实现的？

#### RedissonLock.tryLock() 源码分析

```java
public class RedissonLock extends RedissonExpirable implements RLock {
    
    @Override
    public boolean tryLock(long waitTime, long leaseTime, TimeUnit unit) 
            throws InterruptedException {
        
        long time = unit.toMillis(waitTime);
        long current = System.currentTimeMillis();
        long threadId = Thread.currentThread().getId();
        
        // 1. 尝试获取锁
        Long ttl = tryAcquire(leaseTime, unit, threadId);
        
        // 2. 获取成功，返回 true
        if (ttl == null) {
            return true;
        }
        
        // 3. 获取失败，判断是否超时
        time -= System.currentTimeMillis() - current;
        if (time <= 0) {
            acquireFailed(threadId);
            return false;
        }
        
        current = System.currentTimeMillis();
        
        // 4. 订阅锁释放事件
        RFuture<RedissonLockEntry> subscribeFuture = 
                subscribe(threadId);
        
        // 5. 等待订阅完成（带超时）
        if (!subscribeFuture.await(time, TimeUnit.MILLISECONDS)) {
            if (!subscribeFuture.cancel(false)) {
                subscribeFuture.onComplete((res, e) -> {
                    if (e == null) {
                        unsubscribe(subscribeFuture, threadId);
                    }
                });
            }
            acquireFailed(threadId);
            return false;
        }
        
        try {
            // 6. 循环尝试获取锁
            time -= System.currentTimeMillis() - current;
            if (time <= 0) {
                acquireFailed(threadId);
                return false;
            }
            
            while (true) {
                long currentTime = System.currentTimeMillis();
                
                // 7. 再次尝试获取锁
                ttl = tryAcquire(leaseTime, unit, threadId);
                
                // 8. 获取成功
                if (ttl == null) {
                    return true;
                }
                
                // 9. 判断是否超时
                time -= System.currentTimeMillis() - currentTime;
                if (time <= 0) {
                    acquireFailed(threadId);
                    return false;
                }
                
                // 10. 等待信号（锁释放通知）
                currentTime = System.currentTimeMillis();
                if (ttl >= 0 && ttl < time) {
                    // 等待 ttl 时间（锁即将过期）
                    getEntry(threadId).getLatch()
                            .tryAcquire(ttl, TimeUnit.MILLISECONDS);
                } else {
                    // 等待 time 时间（等待超时）
                    getEntry(threadId).getLatch()
                            .tryAcquire(time, TimeUnit.MILLISECONDS);
                }
                
                time -= System.currentTimeMillis() - currentTime;
                if (time <= 0) {
                    acquireFailed(threadId);
                    return false;
                }
            }
        } finally {
            // 11. 取消订阅
            unsubscribe(subscribeFuture, threadId);
        }
    }
    
    /**
     * 尝试获取锁（执行 Lua 脚本）
     */
    private Long tryAcquire(long leaseTime, TimeUnit unit, long threadId) {
        return get(tryAcquireAsync(leaseTime, unit, threadId));
    }
    
    private <T> RFuture<Long> tryAcquireAsync(long leaseTime, TimeUnit unit, 
                                               long threadId) {
        // 如果指定了过期时间
        if (leaseTime != -1) {
            return tryLockInnerAsync(leaseTime, unit, threadId, 
                    RedisCommands.EVAL_LONG);
        }
        
        // 如果没有指定过期时间，使用默认的 30 秒，并启动 WatchDog
        RFuture<Long> ttlRemainingFuture = tryLockInnerAsync(
                commandExecutor.getConnectionManager().getCfg()
                        .getLockWatchdogTimeout(),
                TimeUnit.MILLISECONDS, threadId, RedisCommands.EVAL_LONG);
        
        ttlRemainingFuture.onComplete((ttlRemaining, e) -> {
            if (e != null) {
                return;
            }
            
            // 加锁成功，启动 WatchDog
            if (ttlRemaining == null) {
                scheduleExpirationRenewal(threadId);
            }
        });
        
        return ttlRemainingFuture;
    }
    
    /**
     * 执行加锁 Lua 脚本
     */
    <T> RFuture<T> tryLockInnerAsync(long leaseTime, TimeUnit unit, 
                                      long threadId, RedisStrictCommand<T> command) {
        internalLockLeaseTime = unit.toMillis(leaseTime);
        
        return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, command,
                // Lua 脚本
                "if (redis.call('exists', KEYS[1]) == 0) then " +
                    "redis.call('hincrby', KEYS[1], ARGV[2], 1); " +
                    "redis.call('pexpire', KEYS[1], ARGV[1]); " +
                    "return nil; " +
                "end; " +
                "if (redis.call('hexists', KEYS[1], ARGV[2]) == 1) then " +
                    "redis.call('hincrby', KEYS[1], ARGV[2], 1); " +
                    "redis.call('pexpire', KEYS[1], ARGV[1]); " +
                    "return nil; " +
                "end; " +
                "return redis.call('pttl', KEYS[1]);",
                
                // 参数
                Collections.singletonList(getName()),  // KEYS[1]
                internalLockLeaseTime,                 // ARGV[1]
                getLockName(threadId));                // ARGV[2]
    }
    
    /**
     * 获取锁的唯一标识
     */
    protected String getLockName(long threadId) {
        return id + ":" + threadId;
    }
}
```

---

## 三、解锁流程详解

### 问题6：Redisson 解锁的流程是什么？

#### 流程图

```
客户端调用 lock.unlock()
         ↓
    执行 Lua 解锁脚本
         ↓
    判断返回值
    ├─ 1 → 解锁成功（锁已完全释放）
    │      ↓
    │  发布锁释放消息
    │      ↓
    │  取消 WatchDog 定时任务
    │      ↓
    │  返回成功
    │
    ├─ 0 → 锁不存在或不是当前线程持有
    │      ↓
    │  抛出 IllegalMonitorStateException
    │
    └─ 其他 → 锁仍被当前线程持有（重入次数-1）
           ↓
       返回成功
```

---

### 问题7：解锁的 Lua 脚本是如何实现的？

```lua
-- KEYS[1]: 锁的key
-- KEYS[2]: 发布订阅的channel，例如 "redisson_lock__channel:{myLock}"
-- ARGV[1]: 发布的消息，固定为 0（表示锁释放）
-- ARGV[2]: 锁的过期时间
-- ARGV[3]: 锁的唯一标识

-- 判断锁是否存在
if (redis.call('hexists', KEYS[1], ARGV[3]) == 0) then
    -- 锁不存在，返回 nil
    return nil;
end;

-- 重入次数-1
local counter = redis.call('hincrby', KEYS[1], ARGV[3], -1);

-- 判断重入次数是否大于0
if (counter > 0) then
    -- 还有重入，刷新过期时间
    redis.call('pexpire', KEYS[1], ARGV[2]);
    return 0;
else
    -- 完全释放锁
    redis.call('del', KEYS[1]);
    -- 发布锁释放消息
    redis.call('publish', KEYS[2], ARGV[1]);
    return 1;
end;

return nil;
```

#### 脚本解析

**场景1：正常解锁**

```
初始状态：
myLock (Hash)
  └─ uuid:thread-1 → 1

执行解锁：
1. hincrby myLock uuid:thread-1 -1  → 0
2. del myLock
3. publish redisson_lock__channel:{myLock} 0
```

**场景2：可重入锁解锁**

```
初始状态：
myLock (Hash)
  └─ uuid:thread-1 → 3

第一次解锁：
1. hincrby myLock uuid:thread-1 -1  → 2
2. pexpire myLock 30000
3. 返回 0（锁未完全释放）

第二次解锁：
1. hincrby myLock uuid:thread-1 -1  → 1
2. pexpire myLock 30000
3. 返回 0

第三次解锁：
1. hincrby myLock uuid:thread-1 -1  → 0
2. del myLock
3. publish redisson_lock__channel:{myLock} 0
4. 返回 1（锁完全释放）
```

---

### 问题8：解锁的 Java 源码是如何实现的？

```java
public class RedissonLock extends RedissonExpirable implements RLock {
    
    @Override
    public void unlock() {
        try {
            get(unlockAsync(Thread.currentThread().getId()));
        } catch (RedisException e) {
            if (e.getCause() instanceof IllegalMonitorStateException) {
                throw (IllegalMonitorStateException) e.getCause();
            } else {
                throw e;
            }
        }
    }
    
    @Override
    public RFuture<Void> unlockAsync(long threadId) {
        RPromise<Void> result = new RedissonPromise<Void>();
        
        // 执行解锁 Lua 脚本
        RFuture<Boolean> future = unlockInnerAsync(threadId);
        
        future.onComplete((opStatus, e) -> {
            // 取消 WatchDog 定时任务
            cancelExpirationRenewal(threadId);
            
            if (e != null) {
                result.tryFailure(e);
                return;
            }
            
            if (opStatus == null) {
                // 锁不存在或不是当前线程持有
                IllegalMonitorStateException cause = 
                        new IllegalMonitorStateException(
                                "attempt to unlock lock, not locked by current thread by node id: "
                                + id + " thread-id: " + threadId);
                result.tryFailure(cause);
                return;
            }
            
            result.trySuccess(null);
        });
        
        return result;
    }
    
    /**
     * 执行解锁 Lua 脚本
     */
    protected RFuture<Boolean> unlockInnerAsync(long threadId) {
        return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, 
                RedisCommands.EVAL_BOOLEAN,
                
                // Lua 脚本
                "if (redis.call('hexists', KEYS[1], ARGV[3]) == 0) then " +
                    "return nil;" +
                "end; " +
                "local counter = redis.call('hincrby', KEYS[1], ARGV[3], -1); " +
                "if (counter > 0) then " +
                    "redis.call('pexpire', KEYS[1], ARGV[2]); " +
                    "return 0; " +
                "else " +
                    "redis.call('del', KEYS[1]); " +
                    "redis.call('publish', KEYS[2], ARGV[1]); " +
                    "return 1; " +
                "end; " +
                "return nil;",
                
                // 参数
                Arrays.asList(getName(), getChannelName()),  // KEYS[1], KEYS[2]
                LockPubSub.UNLOCK_MESSAGE,                   // ARGV[1] = 0
                internalLockLeaseTime,                       // ARGV[2]
                getLockName(threadId));                      // ARGV[3]
    }
    
    /**
     * 获取发布订阅的 channel 名称
     */
    protected String getChannelName() {
        return "redisson_lock__channel:{" + getName() + "}";
    }
}
```

---

## 四、发布订阅机制

### 问题9：为什么需要发布订阅机制？

#### 对比：轮询 vs 发布订阅

**方案1：轮询（性能差）**

```java
while (true) {
    if (tryLock()) {
        break;
    }
    Thread.sleep(100); // 每100ms尝试一次
}
```

**问题**：
- 浪费 CPU 资源
- 响应延迟（最多100ms）
- 增加 Redis 压力

**方案2：发布订阅（高效）**

```java
// 订阅锁释放事件
subscribe("redisson_lock__channel:{myLock}");

// 阻塞等待
semaphore.acquire();

// 收到通知后立即尝试获取锁
tryLock();
```

**优点**：
- 不浪费 CPU
- 响应及时（毫秒级）
- 减少 Redis 压力

---

### 问题10：发布订阅的实现原理是什么？

#### 流程图

```
┌─────────────┐                    ┌─────────────┐
│  客户端A    │                    │  客户端B    │
│  (持有锁)   │                    │  (等待锁)   │
└──────┬──────┘                    └──────┬──────┘
       │                                  │
       │ 1. 订阅 channel                  │
       │  ─────────────────────────────→  │
       │                                  │
       │                                  │ 2. 阻塞等待
       │                                  │    (Semaphore)
       │                                  │
       │ 3. unlock()                      │
       │    执行 Lua 脚本                  │
       │    ├─ del myLock                 │
       │    └─ publish channel 0          │
       │  ─────────────────────────────→  │
       │                                  │
       │                                  │ 4. 收到消息
       │                                  │    释放 Semaphore
       │                                  │
       │                                  │ 5. 尝试获取锁
       │                                  │    tryLock()
       │                                  │
```

#### 核心代码

```java
public class LockPubSub extends PublishSubscribe<RedissonLockEntry> {
    
    public static final Long UNLOCK_MESSAGE = 0L;
    
    @Override
    protected RedissonLockEntry createEntry(RPromise<RedissonLockEntry> newPromise) {
        return new RedissonLockEntry(newPromise);
    }
    
    @Override
    protected void onMessage(RedissonLockEntry value, Long message) {
        // 收到锁释放消息
        if (message.equals(UNLOCK_MESSAGE)) {
            Runnable runnableToExecute = value.getListeners().poll();
            if (runnableToExecute != null) {
                runnableToExecute.run();
            }
            
            // 释放信号量，唤醒等待的线程
            value.getLatch().release();
        }
    }
}

public class RedissonLockEntry {
    
    private final Semaphore latch;
    private final Queue<Runnable> listeners = new ConcurrentLinkedQueue<>();
    
    public RedissonLockEntry(RPromise<RedissonLockEntry> promise) {
        this.latch = new Semaphore(0);
    }
    
    public Semaphore getLatch() {
        return latch;
    }
    
    public void addListener(Runnable listener) {
        listeners.add(listener);
    }
    
    public Queue<Runnable> getListeners() {
        return listeners;
    }
}
```

---

## 五、核心设计思想

### 问题11：Redisson 的核心设计思想有哪些？

#### 1. 原子性保证

**问题**：如何保证加锁、解锁的原子性？

**方案**：Lua 脚本

```lua
-- 加锁、设置过期时间、重入计数 都在一个脚本中完成
-- Redis 保证 Lua 脚本的原子执行
```

#### 2. 可重入性

**问题**：如何支持同一线程多次获取锁？

**方案**：Hash 结构 + 计数器

```
myLock (Hash)
  └─ uuid:threadId → 重入次数
```

#### 3. 防死锁

**问题**：如何防止客户端崩溃导致锁无法释放？

**方案**：过期时间 + WatchDog 自动续期

```java
// 设置过期时间
redis.call('pexpire', KEYS[1], 30000);

// WatchDog 每10秒续期一次
scheduleExpirationRenewal(threadId);
```

#### 4. 高性能

**问题**：如何减少不必要的轮询？

**方案**：发布订阅 + Semaphore

```java
// 订阅锁释放事件
subscribe(channelName);

// 阻塞等待（不占用 CPU）
semaphore.acquire();
```

#### 5. 公平性（可选）

**问题**：如何保证先到先得？

**方案**：基于 List 的队列（FairLock）

```lua
-- 将请求加入队列
redis.call('lpush', 'queue', threadId);

-- 判断是否是队列头部
if redis.call('lindex', 'queue', 0) == threadId then
    -- 获取锁
end
```

---

## 六、总结

### 核心要点

1. **Lua 脚本保证原子性**
   - 加锁、解锁都使用 Lua 脚本
   - 避免竞态条件

2. **Hash 结构支持可重入**
   - field 存储线程标识
   - value 存储重入次数

3. **发布订阅提高性能**
   - 避免无效轮询
   - 及时响应锁释放

4. **WatchDog 自动续期**
   - 防止业务未完成锁就过期
   - 定时任务每 10 秒续期

5. **Semaphore 阻塞等待**
   - 不占用 CPU
   - 收到通知立即唤醒

### 关键数据结构

```
Redis 中的数据：
┌──────────────────────────────────────┐
│  myLock (Hash)                       │
│    └─ uuid:threadId → 重入次数       │
├──────────────────────────────────────┤
│  redisson_lock__channel:{myLock}     │
│    (发布订阅 channel)                │
└──────────────────────────────────────┘

Java 中的数据：
┌──────────────────────────────────────┐
│  RedissonLockEntry                   │
│    ├─ Semaphore (阻塞等待)           │
│    └─ Queue<Runnable> (监听器)       │
└──────────────────────────────────────┘
```

---

## 七、思考题

1. **为什么 Redisson 使用 Hash 而不是 String 存储锁？**
   - 提示：可重入性、多线程

2. **如果 Lua 脚本执行失败怎么办？**
   - 提示：Redis 的事务机制、脚本回滚

3. **发布订阅机制会不会丢失消息？**
   - 提示：Redis 的 pub/sub 特性、重试机制

4. **为什么解锁时要发布消息？**
   - 提示：通知等待的客户端、减少轮询

---

**下一篇**：[04-锁续期机制WatchDog源码分析.md](./04-锁续期机制WatchDog源码分析.md) - 深入剖析 WatchDog 的自动续期机制
