package com.huabin.redis.service;

import com.alibaba.fastjson.JSON;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pipeline 高级用法示例
 * 
 * @author huabin
 * @description 演示 Pipeline 的注意事项和最佳实践
 */
@Service
public class PipelineAdvancedService {
    
    @Autowired
    private StringRedisTemplate redisTemplate;
    
    /**
     * ❌ 错误示例：Pipeline 不是事务
     * 
     * 问题：如果中间某个命令失败，其他命令仍会执行
     */
    public void pipelineIsNotTransaction() {
        redisTemplate.executePipelined(new RedisCallback<Object>() {
            @Override
            public Object doInRedis(RedisConnection connection) throws DataAccessException {
                connection.set("key1".getBytes(), "value1".getBytes());
                // 假设这个命令失败
                connection.incr("not_a_number".getBytes()); // 错误：不是数字
                connection.set("key2".getBytes(), "value2".getBytes());
                return null;
            }
        });
        
        // 结果：key1 和 key2 都会被设置，即使中间命令失败
    }
    
    /**
     * ✅ 正确示例：需要原子性使用事务
     */
    public void useTransactionForAtomicity() {
        redisTemplate.execute(new SessionCallback<Object>() {
            @Override
            public Object execute(RedisOperations operations) throws DataAccessException {
                operations.multi(); // 开启事务
                operations.opsForValue().set("key1", "value1");
                operations.opsForValue().increment("counter");
                operations.opsForValue().set("key2", "value2");
                return operations.exec(); // 提交事务
            }
        });
    }
    
    /**
     * ❌ 错误示例：Pipeline 中无法使用前一个命令的结果
     */
    public void pipelineCannotUsePreviousResult() {
        // 这样做是错误的
        redisTemplate.executePipelined(new RedisCallback<Object>() {
            @Override
            public Object doInRedis(RedisConnection connection) throws DataAccessException {
                // 无法获取这个命令的返回值
                byte[] value = connection.get("key1".getBytes());
                
                // ❌ value 是 null，无法使用
                // connection.set("key2".getBytes(), value);
                
                return null;
            }
        });
    }
    
    /**
     * ✅ 正确示例：需要依赖结果时，分开执行
     */
    public void executeSequentially() {
        // 先获取值
        String value = redisTemplate.opsForValue().get("key1");
        
        // 再使用这个值
        if (value != null) {
            redisTemplate.opsForValue().set("key2", value);
        }
    }
    
    /**
     * 秒杀系统：使用 Pipeline 批量预热库存
     * 
     * 场景：秒杀开始前，将 10000 个商品的库存写入 Redis
     * 使用 Pipeline 批量写入，提升性能
     */
    public void preloadSeckillStock(List<SeckillProduct> products) {
        long start = System.currentTimeMillis();
        
        // 分批执行，每批 500 个
        int batchSize = 500;
        for (int i = 0; i < products.size(); i += batchSize) {
            int end = Math.min(i + batchSize, products.size());
            List<SeckillProduct> batch = products.subList(i, end);
            
            redisTemplate.executePipelined(new RedisCallback<Object>() {
                @Override
                public Object doInRedis(RedisConnection connection) throws DataAccessException {
                    for (SeckillProduct product : batch) {
                        // 设置库存
                        String stockKey = "seckill:stock:" + product.getId();
                        connection.set(stockKey.getBytes(), 
                                     String.valueOf(product.getStock()).getBytes());
                        
                        // 设置商品信息
                        String infoKey = "seckill:info:" + product.getId();
                        connection.set(infoKey.getBytes(), 
                                     JSON.toJSONString(product).getBytes());
                        
                        // 设置过期时间（秒杀结束后 1 小时）
                        connection.expire(stockKey.getBytes(), 7200);
                        connection.expire(infoKey.getBytes(), 7200);
                    }
                    return null;
                }
            });
        }
        
        long cost = System.currentTimeMillis() - start;
        System.out.println("预热 " + products.size() + " 个商品，耗时: " + cost + "ms");
    }
    
    /**
     * 批量查询商品库存
     */
    public Map<Long, Integer> batchGetSeckillStock(List<Long> productIds) {
        List<Object> results = redisTemplate.executePipelined(new RedisCallback<Object>() {
            @Override
            public Object doInRedis(RedisConnection connection) throws DataAccessException {
                for (Long productId : productIds) {
                    String stockKey = "seckill:stock:" + productId;
                    connection.get(stockKey.getBytes());
                }
                return null;
            }
        });
        
        // 解析结果
        Map<Long, Integer> stockMap = new HashMap<>();
        for (int i = 0; i < productIds.size(); i++) {
            Object result = results.get(i);
            if (result != null) {
                int stock = Integer.parseInt(new String((byte[]) result));
                stockMap.put(productIds.get(i), stock);
            }
        }
        
        return stockMap;
    }
    
    /**
     * 秒杀商品实体类
     */
    public static class SeckillProduct {
        private Long id;
        private String name;
        private Integer stock;
        private Double price;
        
        public SeckillProduct() {
        }
        
        public SeckillProduct(Long id, String name, Integer stock, Double price) {
            this.id = id;
            this.name = name;
            this.stock = stock;
            this.price = price;
        }
        
        public Long getId() {
            return id;
        }
        
        public void setId(Long id) {
            this.id = id;
        }
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public Integer getStock() {
            return stock;
        }
        
        public void setStock(Integer stock) {
            this.stock = stock;
        }
        
        public Double getPrice() {
            return price;
        }
        
        public void setPrice(Double price) {
            this.price = price;
        }
    }
}
