# API并发请求限流

- **需求背景：** 在数据量比较大的业务场景中，上传导出接口一般都比较慢，不限制并发数的话会造成服务器压力过大，故需要限制接口的最大并发数。

[限流相关的背景知识](/Users/huabin/workspace/playground/springboot/notes/限流的相关知识.md)

[限流实现](https://juejin.cn/post/7145435951899574302)

## 单机限流

### 使用Semaphore实现一个简单的限流器

这种实现方式实现的是计数器限流，限制的是一个api接口的并发数量。

[参考](https://juejin.cn/post/7023760925513498632)

### 基于Guava Ratelimiter


## 分布式限流

### Redisson


### Zookeeper
