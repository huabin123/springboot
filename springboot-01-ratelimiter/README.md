# API并发请求限流

- **需求背景：** 在数据量比较大的业务场景中，上传导出接口一般都比较慢，不限制并发数的话会造成服务器压力过大，故需要限制接口的最大并发数。

[限流实现](https://juejin.cn/post/7145435951899574302)

## 单机限流

### 使用Semaphore实现一个简单的限流器

[参考](https://juejin.cn/post/7023760925513498632)

### 基于Guava Ratelimiter


## 分布式限流

### Redisson


### Zookeeper
