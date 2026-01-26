# Redis 与 Netty 的高性能设计对比

> **学习目标**：深入理解 Redis 和 Netty 在高性能设计上的共同思想，掌握高性能系统的设计精髓。

## 一、架构对比概览

### 1.1 核心相似点

```
Redis 和 Netty 的共同设计理念：

1. 事件驱动架构（Event-Driven）
2. IO 多路复用（epoll/kqueue）
3. 单线程事件循环（EventLoop）
4. 非阻塞 IO（Non-blocking IO）
5. 零拷贝优化（Zero-Copy）
6. 内存池管理（Memory Pool）
```

### 1.2 架构对比图

```
┌─────────────────────────────────────────────────────────────┐
│                    Redis 架构                                │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌──────────────────────────────────────────────────┐       │
│  │           单线程事件循环（Main Thread）          │       │
│  ├──────────────────────────────────────────────────┤       │
│  │                                                    │       │
│  │  ┌─────────────┐        ┌─────────────┐         │       │
│  │  │  File Event │        │ Time Event  │         │       │
│  │  │  (网络IO)   │        │  (定时任务)  │         │       │
│  │  └─────────────┘        └─────────────┘         │       │
│  │         │                       │                 │       │
│  │         ↓                       ↓                 │       │
│  │  ┌─────────────────────────────────────┐        │       │
│  │  │        epoll/kqueue                  │        │       │
│  │  │    (IO 多路复用)                     │        │       │
│  │  └─────────────────────────────────────┘        │       │
│  └──────────────────────────────────────────────────┘       │
│                                                               │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                    Netty 架构                                │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌──────────────────────────────────────────────────┐       │
│  │              EventLoopGroup                       │       │
│  ├──────────────────────────────────────────────────┤       │
│  │                                                    │       │
│  │  ┌─────────┐  ┌─────────┐  ┌─────────┐          │       │
│  │  │EventLoop│  │EventLoop│  │EventLoop│          │       │
│  │  │(Thread1)│  │(Thread2)│  │(Thread3)│          │       │
│  │  └────┬────┘  └────┬────┘  └────┬────┘          │       │
│  │       │            │            │                 │       │
│  │       ↓            ↓            ↓                 │       │
│  │  ┌─────────────────────────────────────┐        │       │
│  │  │        epoll/kqueue                  │        │       │
│  │  │    (IO 多路复用)                     │        │       │
│  │  └─────────────────────────────────────┘        │       │
│  └──────────────────────────────────────────────────┘       │
│                                                               │
└─────────────────────────────────────────────────────────────┘

核心区别：
- Redis：单线程 EventLoop
- Netty：多线程 EventLoopGroup（每个线程一个 EventLoop）
```

---

## 二、事件驱动架构

### 2.1 Redis 的事件循环

```c
// Redis 事件循环伪代码
void aeMain(aeEventLoop *eventLoop) {
    eventLoop->stop = 0;
    
    while (!eventLoop->stop) {
        // 1. 处理到期的时间事件
        processTimeEvents(eventLoop);
        
        // 2. 等待文件事件（阻塞在 epoll_wait）
        int numEvents = aeApiPoll(eventLoop, tvp);
        
        // 3. 处理文件事件
        for (int i = 0; i < numEvents; i++) {
            aeFileEvent *fe = &eventLoop->events[eventLoop->fired[i].fd];
            
            // 可读事件
            if (fe->mask & AE_READABLE) {
                fe->rfileProc(eventLoop, fd, fe->clientData, mask);
            }
            
            // 可写事件
            if (fe->mask & AE_WRITABLE) {
                fe->wfileProc(eventLoop, fd, fe->clientData, mask);
            }
        }
    }
}
```

### 2.2 Netty 的事件循环

```java
// Netty EventLoop 核心代码
public final class NioEventLoop extends SingleThreadEventLoop {
    
    @Override
    protected void run() {
        for (;;) {
            try {
                // 1. 选择策略：是否需要阻塞
                switch (selectStrategy.calculateStrategy(selectNowSupplier, hasTasks())) {
                    case SelectStrategy.CONTINUE:
                        continue;
                    case SelectStrategy.SELECT:
                        // 2. 阻塞在 select（类似 Redis 的 epoll_wait）
                        select(wakenUp.getAndSet(false));
                        
                        if (wakenUp.get()) {
                            selector.wakeup();
                        }
                    default:
                }
                
                cancelledKeys = 0;
                needsToSelectAgain = false;
                final int ioRatio = this.ioRatio;
                
                if (ioRatio == 100) {
                    // 3. 处理 IO 事件
                    processSelectedKeys();
                    // 4. 处理任务队列
                    runAllTasks();
                } else {
                    final long ioStartTime = System.nanoTime();
                    
                    // 3. 处理 IO 事件
                    processSelectedKeys();
                    
                    final long ioTime = System.nanoTime() - ioStartTime;
                    
                    // 4. 处理任务队列（按比例分配时间）
                    runAllTasks(ioTime * (100 - ioRatio) / ioRatio);
                }
            } catch (Throwable t) {
                handleLoopException(t);
            }
        }
    }
}
```

### 2.3 对比分析

| 特性 | Redis | Netty | 设计思想 |
|------|-------|-------|----------|
| **事件循环** | 单线程 | 多线程（每个线程一个循环） | 都是 Reactor 模式 |
| **IO 多路复用** | epoll/kqueue | epoll/kqueue | 相同的底层技术 |
| **事件类型** | 文件事件 + 时间事件 | IO 事件 + 任务队列 | 都支持多种事件 |
| **阻塞点** | epoll_wait | selector.select | 都阻塞在 IO 多路复用 |
| **非阻塞 IO** | ✅ | ✅ | 避免线程阻塞 |

**共同点：**
1. 都使用 **Reactor 模式**
2. 都基于 **IO 多路复用**
3. 都是 **事件驱动**
4. 都是 **非阻塞 IO**

---

## 三、IO 多路复用深度对比

### 3.1 Redis 的 IO 多路复用

```c
// Redis 的 epoll 封装
static int aeApiPoll(aeEventLoop *eventLoop, struct timeval *tvp) {
    aeApiState *state = eventLoop->apidata;
    int retval, numevents = 0;
    
    // 阻塞等待事件（类似 Netty 的 selector.select）
    retval = epoll_wait(state->epfd, 
                        state->events, 
                        eventLoop->setsize,
                        tvp ? (tvp->tv_sec*1000 + tvp->tv_usec/1000) : -1);
    
    if (retval > 0) {
        numevents = retval;
        for (int i = 0; i < numevents; i++) {
            int mask = 0;
            struct epoll_event *e = state->events + i;
            
            if (e->events & EPOLLIN) mask |= AE_READABLE;
            if (e->events & EPOLLOUT) mask |= AE_WRITABLE;
            if (e->events & EPOLLERR) mask |= AE_WRITABLE;
            if (e->events & EPOLLHUP) mask |= AE_WRITABLE;
            
            eventLoop->fired[i].fd = e->data.fd;
            eventLoop->fired[i].mask = mask;
        }
    }
    
    return numevents;
}
```

### 3.2 Netty 的 IO 多路复用

```java
// Netty 的 epoll 封装
public final class EpollEventLoop extends SingleThreadEventLoop {
    
    private void select(boolean oldWakenUp) throws IOException {
        Selector selector = this.selector;
        
        try {
            int selectCnt = 0;
            long currentTimeNanos = System.nanoTime();
            long selectDeadLineNanos = currentTimeNanos + delayNanos(currentTimeNanos);
            
            for (;;) {
                long timeoutMillis = (selectDeadLineNanos - currentTimeNanos + 500000L) / 1000000L;
                
                if (timeoutMillis <= 0) {
                    if (selectCnt == 0) {
                        selector.selectNow();
                        selectCnt = 1;
                    }
                    break;
                }
                
                // 阻塞等待事件（类似 Redis 的 epoll_wait）
                int selectedKeys = selector.select(timeoutMillis);
                selectCnt++;
                
                if (selectedKeys != 0 || oldWakenUp || wakenUp.get() || hasTasks() || hasScheduledTasks()) {
                    break;
                }
                
                // 处理 JDK epoll bug（空轮询）
                if (SELECTOR_AUTO_REBUILD_THRESHOLD > 0 &&
                    selectCnt >= SELECTOR_AUTO_REBUILD_THRESHOLD) {
                    rebuildSelector();
                    selector = this.selector;
                    selector.selectNow();
                    selectCnt = 1;
                    break;
                }
                
                currentTimeNanos = System.nanoTime();
            }
        } catch (CancelledKeyException e) {
            // Harmless exception
        }
    }
}
```

### 3.3 性能优化对比

| 优化点 | Redis | Netty | 效果 |
|--------|-------|-------|------|
| **边缘触发** | 支持 ET 模式 | 支持 ET 模式 | 减少事件通知次数 |
| **批量处理** | 一次处理多个事件 | 一次处理多个事件 | 提高吞吐量 |
| **避免惊群** | 单线程无惊群 | 每个线程独立 selector | 避免资源竞争 |
| **JDK Bug** | 不涉及 | 自动重建 selector | 避免空轮询 |

---

## 四、内存管理对比

### 4.1 Redis 的内存管理

```c
// Redis 使用 jemalloc 内存分配器
void *zmalloc(size_t size) {
    void *ptr = je_malloc(size + PREFIX_SIZE);
    
    if (!ptr) zmalloc_oom_handler(size);
    
    // 记录已分配内存
    update_zmalloc_stat_alloc(zmalloc_size(ptr));
    
    return ptr;
}

// 内存池思想：SDS 的空间预分配
sds sdsMakeRoomFor(sds s, size_t addlen) {
    size_t len = sdslen(s);
    size_t newlen = len + addlen;
    
    // 空间预分配策略
    if (newlen < SDS_MAX_PREALLOC) {
        newlen *= 2;  // 小于 1MB，分配 2 倍空间
    } else {
        newlen += SDS_MAX_PREALLOC;  // 大于 1MB，额外分配 1MB
    }
    
    // 重新分配内存
    s = zrealloc(s, newlen);
    return s;
}
```

### 4.2 Netty 的内存管理

```java
// Netty 的内存池实现
public class PooledByteBufAllocator extends AbstractByteBufAllocator {
    
    // 内存池：按线程缓存
    private final PoolThreadLocalCache threadCache;
    
    @Override
    protected ByteBuf newDirectBuffer(int initialCapacity, int maxCapacity) {
        // 1. 从线程本地缓存获取
        PoolThreadCache cache = threadCache.get();
        PoolArena<ByteBuffer> directArena = cache.directArena;
        
        if (directArena != null) {
            // 2. 从 Arena 分配内存
            return directArena.allocate(cache, initialCapacity, maxCapacity);
        } else {
            // 3. 直接分配（无池化）
            return new UnpooledDirectByteBuf(this, initialCapacity, maxCapacity);
        }
    }
}

// 内存分配策略（类似 Redis 的预分配）
public class AdaptiveRecvByteBufAllocator implements RecvByteBufAllocator {
    
    private static final int[] SIZE_TABLE;
    
    static {
        List<Integer> sizeTable = new ArrayList<>();
        
        // 16 字节开始，每次增加 16
        for (int i = 16; i < 512; i += 16) {
            sizeTable.add(i);
        }
        
        // 512 字节开始，每次翻倍
        for (int i = 512; i > 0; i <<= 1) {
            sizeTable.add(i);
        }
        
        SIZE_TABLE = new int[sizeTable.size()];
        for (int i = 0; i < SIZE_TABLE.length; i++) {
            SIZE_TABLE[i] = sizeTable.get(i);
        }
    }
}
```

### 4.3 内存管理对比

| 特性 | Redis | Netty | 共同点 |
|------|-------|-------|--------|
| **内存分配器** | jemalloc | jemalloc（可选） | 高效的内存分配 |
| **内存池** | SDS 预分配 | PooledByteBuf | 减少分配/释放开销 |
| **对象复用** | ✅ | ✅ | 避免频繁 GC |
| **零拷贝** | ✅ | ✅ | 减少内存拷贝 |

---

## 五、零拷贝技术

### 5.1 Redis 的零拷贝

```c
// Redis 使用 sendfile 系统调用
ssize_t rioWriteBulkFile(rio *r, const char *buf, size_t len) {
    ssize_t nwritten;
    
    // 直接从文件发送到 socket，无需经过用户空间
    nwritten = sendfile(r->io.file.fd, 
                       r->io.file.buffered, 
                       NULL, 
                       len);
    
    return nwritten;
}

// RDB 持久化时的零拷贝
int rdbSaveRio(rio *rdb, int *error, int flags, rdbSaveInfo *rsi) {
    // 直接写入文件，避免内存拷贝
    if (rioWrite(rdb, magic, 9) == 0) goto werr;
    if (rdbSaveInfoAuxFields(rdb, flags, rsi) == -1) goto werr;
    
    // ...
}
```

### 5.2 Netty 的零拷贝

```java
// Netty 的零拷贝实现
public class NettyZeroCopyExample {
    
    /**
     * 1. CompositeByteBuf：组合多个 ByteBuf，避免内存拷贝
     */
    public void compositeByteBuf() {
        ByteBuf header = Unpooled.buffer(10);
        ByteBuf body = Unpooled.buffer(100);
        
        // 传统方式：需要拷贝
        // ByteBuf combined = Unpooled.buffer(110);
        // combined.writeBytes(header);
        // combined.writeBytes(body);
        
        // 零拷贝方式：只是组合引用
        CompositeByteBuf compositeBuf = Unpooled.compositeBuffer();
        compositeBuf.addComponents(true, header, body);
    }
    
    /**
     * 2. slice：切片，共享底层数组
     */
    public void sliceByteBuf() {
        ByteBuf buffer = Unpooled.buffer(100);
        
        // 切片：不拷贝数据，只是创建新的视图
        ByteBuf slice1 = buffer.slice(0, 50);
        ByteBuf slice2 = buffer.slice(50, 50);
    }
    
    /**
     * 3. FileRegion：文件传输零拷贝
     */
    public void fileRegion(SocketChannel channel, File file) throws IOException {
        FileChannel fileChannel = new FileInputStream(file).getChannel();
        
        // 使用 sendfile 系统调用，零拷贝传输文件
        FileRegion region = new DefaultFileRegion(fileChannel, 0, file.length());
        channel.writeAndFlush(region);
    }
    
    /**
     * 4. DirectByteBuf：直接内存，避免 JVM 堆拷贝
     */
    public void directByteBuf() {
        // 直接内存：socket 读写时无需从堆内存拷贝到直接内存
        ByteBuf directBuffer = Unpooled.directBuffer(1024);
    }
}
```

### 5.3 零拷贝对比

```
传统 IO 流程（4 次拷贝，4 次上下文切换）：
┌─────────┐
│  磁盘   │
└────┬────┘
     │ 1. DMA 拷贝到内核缓冲区
     ↓
┌─────────┐
│内核缓冲区│
└────┬────┘
     │ 2. CPU 拷贝到用户缓冲区
     ↓
┌─────────┐
│用户缓冲区│
└────┬────┘
     │ 3. CPU 拷贝到 Socket 缓冲区
     ↓
┌─────────┐
│Socket缓冲│
└────┬────┘
     │ 4. DMA 拷贝到网卡
     ↓
┌─────────┐
│  网卡   │
└─────────┘

零拷贝流程（2 次拷贝，2 次上下文切换）：
┌─────────┐
│  磁盘   │
└────┬────┘
     │ 1. DMA 拷贝到内核缓冲区
     ↓
┌─────────┐
│内核缓冲区│
└────┬────┘
     │ 2. DMA 拷贝到网卡（sendfile）
     ↓
┌─────────┐
│  网卡   │
└─────────┘

性能提升：
- 减少 2 次 CPU 拷贝
- 减少 2 次上下文切换
- 吞吐量提升 2-3 倍
```

---

## 六、Pipeline 批量处理

### 6.1 Redis Pipeline

```java
@Service
public class RedisPipelineExample {
    
    @Autowired
    private StringRedisTemplate redisTemplate;
    
    /**
     * Redis Pipeline：批量发送命令
     */
    public void pipelineExample() {
        long start = System.currentTimeMillis();
        
        // 批量执行 1000 个命令
        List<Object> results = redisTemplate.executePipelined(
            new RedisCallback<Object>() {
                @Override
                public Object doInRedis(RedisConnection connection) {
                    for (int i = 0; i < 1000; i++) {
                        connection.set(
                            ("key" + i).getBytes(),
                            ("value" + i).getBytes()
                        );
                    }
                    return null;
                }
            }
        );
        
        long cost = System.currentTimeMillis() - start;
        System.out.println("Pipeline 耗时: " + cost + "ms");
        // 约 50ms
    }
    
    /**
     * 不使用 Pipeline：逐个发送命令
     */
    public void noPipelineExample() {
        long start = System.currentTimeMillis();
        
        for (int i = 0; i < 1000; i++) {
            redisTemplate.opsForValue().set("key" + i, "value" + i);
        }
        
        long cost = System.currentTimeMillis() - start;
        System.out.println("No Pipeline 耗时: " + cost + "ms");
        // 约 500ms
    }
}
```

### 6.2 Netty Pipeline

```java
// Netty Pipeline：责任链模式处理事件
public class NettyPipelineExample {
    
    public void setupPipeline() {
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel.class)
            .childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                    ChannelPipeline pipeline = ch.pipeline();
                    
                    // Pipeline 链式处理
                    pipeline.addLast("decoder", new StringDecoder());
                    pipeline.addLast("encoder", new StringEncoder());
                    pipeline.addLast("handler1", new BusinessHandler1());
                    pipeline.addLast("handler2", new BusinessHandler2());
                    pipeline.addLast("handler3", new BusinessHandler3());
                }
            });
    }
    
    /**
     * 事件在 Pipeline 中的流转
     */
    static class BusinessHandler1 extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            // 处理业务逻辑
            System.out.println("Handler1 处理: " + msg);
            
            // 传递给下一个 Handler
            ctx.fireChannelRead(msg);
        }
    }
}
```

### 6.3 Pipeline 对比

| 特性 | Redis Pipeline | Netty Pipeline | 共同点 |
|------|---------------|----------------|--------|
| **目的** | 批量发送命令 | 责任链处理事件 | 提高效率 |
| **减少开销** | 减少网络 RTT | 减少方法调用 | 批量处理 |
| **性能提升** | 10 倍+ | 2-3 倍 | 显著提升 |

---

## 七、高性能设计总结

### 7.1 共同的设计模式

```
1. Reactor 模式
   ├─ 事件驱动
   ├─ IO 多路复用
   └─ 非阻塞 IO

2. 内存池模式
   ├─ 对象复用
   ├─ 预分配
   └─ 减少 GC

3. 零拷贝模式
   ├─ DirectBuffer
   ├─ sendfile
   └─ mmap

4. Pipeline 模式
   ├─ 批量处理
   ├─ 责任链
   └─ 减少开销
```

### 7.2 性能优化对比表

| 优化技术 | Redis | Netty | 性能提升 | 适用场景 |
|----------|-------|-------|----------|----------|
| **IO 多路复用** | ✅ | ✅ | 10-100 倍 | 高并发 |
| **单线程模型** | ✅ | ✅（每个 EventLoop） | 避免锁竞争 | CPU 密集型 |
| **内存池** | ✅ | ✅ | 2-3 倍 | 频繁分配/释放 |
| **零拷贝** | ✅ | ✅ | 2-3 倍 | 大文件传输 |
| **Pipeline** | ✅ | ✅ | 10 倍+ | 批量操作 |
| **事件驱动** | ✅ | ✅ | 异步非阻塞 | IO 密集型 |

---

## 八、实战：借鉴设计思想

### 8.1 实现一个简单的事件循环

```java
/**
 * 借鉴 Redis 和 Netty 的事件循环设计
 */
public class SimpleEventLoop {
    
    private final Selector selector;
    private final Queue<Runnable> taskQueue = new ConcurrentLinkedQueue<>();
    private volatile boolean running = true;
    
    public SimpleEventLoop() throws IOException {
        this.selector = Selector.open();
    }
    
    /**
     * 事件循环（类似 Redis 的 aeMain 和 Netty 的 run）
     */
    public void run() {
        while (running) {
            try {
                // 1. 处理 IO 事件（类似 Redis 的 processFileEvents）
                int readyChannels = selector.select(1000);
                
                if (readyChannels > 0) {
                    Set<SelectionKey> selectedKeys = selector.selectedKeys();
                    Iterator<SelectionKey> iterator = selectedKeys.iterator();
                    
                    while (iterator.hasNext()) {
                        SelectionKey key = iterator.next();
                        iterator.remove();
                        
                        if (key.isReadable()) {
                            handleRead(key);
                        } else if (key.isWritable()) {
                            handleWrite(key);
                        }
                    }
                }
                
                // 2. 处理任务队列（类似 Redis 的 processTimeEvents）
                runAllTasks();
                
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    /**
     * 处理读事件
     */
    private void handleRead(SelectionKey key) {
        SocketChannel channel = (SocketChannel) key.channel();
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        
        try {
            int bytesRead = channel.read(buffer);
            if (bytesRead > 0) {
                buffer.flip();
                // 处理数据
                processData(buffer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 处理写事件
     */
    private void handleWrite(SelectionKey key) {
        SocketChannel channel = (SocketChannel) key.channel();
        ByteBuffer buffer = (ByteBuffer) key.attachment();
        
        try {
            channel.write(buffer);
            if (!buffer.hasRemaining()) {
                // 写完了，取消写事件
                key.interestOps(SelectionKey.OP_READ);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 执行任务队列（类似 Netty 的 runAllTasks）
     */
    private void runAllTasks() {
        Runnable task;
        while ((task = taskQueue.poll()) != null) {
            task.run();
        }
    }
    
    /**
     * 提交任务到队列
     */
    public void execute(Runnable task) {
        taskQueue.offer(task);
        selector.wakeup(); // 唤醒 selector
    }
    
    private void processData(ByteBuffer buffer) {
        // 业务逻辑
    }
}
```

### 8.2 实现一个简单的内存池

```java
/**
 * 借鉴 Netty 的内存池设计
 */
public class SimpleMemoryPool {
    
    // 不同大小的内存块池
    private final Queue<ByteBuffer>[] pools;
    private static final int[] SIZES = {256, 512, 1024, 2048, 4096, 8192};
    
    @SuppressWarnings("unchecked")
    public SimpleMemoryPool() {
        pools = new Queue[SIZES.length];
        for (int i = 0; i < SIZES.length; i++) {
            pools[i] = new ConcurrentLinkedQueue<>();
        }
    }
    
    /**
     * 分配内存（类似 Netty 的 allocate）
     */
    public ByteBuffer allocate(int size) {
        int index = getSizeIndex(size);
        
        if (index >= 0) {
            // 从池中获取
            ByteBuffer buffer = pools[index].poll();
            if (buffer != null) {
                buffer.clear();
                return buffer;
            }
        }
        
        // 池中没有，直接分配
        return ByteBuffer.allocateDirect(size);
    }
    
    /**
     * 释放内存（类似 Netty 的 release）
     */
    public void release(ByteBuffer buffer) {
        int capacity = buffer.capacity();
        int index = getSizeIndex(capacity);
        
        if (index >= 0) {
            // 放回池中
            pools[index].offer(buffer);
        }
        // 否则，让 GC 回收
    }
    
    private int getSizeIndex(int size) {
        for (int i = 0; i < SIZES.length; i++) {
            if (size <= SIZES[i]) {
                return i;
            }
        }
        return -1;
    }
}
```

---

## 九、面试重点

### Q1：Redis 和 Netty 在高性能设计上有什么共同点？

**A：** 五个核心共同点：

1. **事件驱动架构**：都使用 Reactor 模式
2. **IO 多路复用**：都基于 epoll/kqueue
3. **非阻塞 IO**：避免线程阻塞
4. **内存池管理**：对象复用，减少 GC
5. **零拷贝优化**：减少内存拷贝

### Q2：为什么 Redis 是单线程，Netty 是多线程？

**A：**

**Redis 单线程原因：**
- CPU 不是瓶颈（纯内存操作）
- 避免多线程的锁竞争
- 简化设计

**Netty 多线程原因：**
- 充分利用多核 CPU
- 每个 EventLoop 独立，无锁竞争
- 提高并发处理能力

**共同点：**
- 都是单线程 EventLoop（Netty 是多个单线程 EventLoop）
- 都避免了锁竞争

### Q3：什么是零拷贝？Redis 和 Netty 如何实现？

**A：**

**零拷贝：** 减少数据在内存中的拷贝次数

**Redis 实现：**
- sendfile 系统调用
- RDB 持久化直接写文件

**Netty 实现：**
- CompositeByteBuf（组合 Buffer）
- slice（切片共享）
- FileRegion（文件传输）
- DirectByteBuf（直接内存）

---

## 十、实践要点

### 10.1 设计高性能系统的关键

```
1. 选择合适的 IO 模型
   ├─ 高并发：IO 多路复用
   ├─ 低延迟：非阻塞 IO
   └─ 高吞吐：批量处理

2. 优化内存管理
   ├─ 使用内存池
   ├─ 对象复用
   └─ 减少 GC

3. 减少数据拷贝
   ├─ 零拷贝技术
   ├─ 直接内存
   └─ sendfile

4. 批量处理
   ├─ Pipeline
   ├─ 批量 IO
   └─ 减少系统调用
```

### 10.2 何时借鉴 Redis/Netty 设计

```
借鉴 Redis 设计：
✅ 纯内存操作
✅ CPU 密集型
✅ 需要简单高效

借鉴 Netty 设计：
✅ 网络通信
✅ 需要充分利用多核
✅ 复杂的协议处理
```

---

## 十一、总结

### 核心设计思想

```
Redis 和 Netty 的高性能秘诀：

1. 事件驱动 + IO 多路复用
   → 单线程处理海量连接

2. 非阻塞 IO
   → 避免线程阻塞，提高吞吐量

3. 内存池 + 对象复用
   → 减少内存分配/释放开销

4. 零拷贝
   → 减少数据拷贝次数

5. 批量处理
   → 减少网络开销和系统调用

这些设计思想是构建高性能系统的基石！
```

---

**延伸阅读：**
- [04-Redis单线程模型与IO多路复用.md](./04-Redis单线程模型与IO多路复用.md)
- Netty 官方文档：https://netty.io/wiki/
- Linux epoll 手册：man epoll
