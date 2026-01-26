package com.huabin.redis.solution.cache;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import com.huabin.redis.model.Product;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;

/**
 * 缓存穿透解决方案
 * 
 * 方案1：缓存空对象
 * 方案2：布隆过滤器
 */
@Service
public class CachePenetrationSolution {
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    // 布隆过滤器：预计存储1000个商品，误判率0.01
    private BloomFilter<Long> productBloomFilter = BloomFilter.create(
        Funnels.longFunnel(),
        1000,
        0.01
    );
    
    /**
     * 初始化布隆过滤器
     * 将所有存在的商品ID加入布隆过滤器
     */
    @PostConstruct
    public void initBloomFilter() {
        System.out.println("初始化布隆过滤器...");
        // 假设商品ID范围是1-1000
        for (long i = 1; i <= 1000; i++) {
            productBloomFilter.put(i);
        }
        System.out.println("布隆过滤器初始化完成，已加载1000个商品ID");
    }
    
    /**
     * 解决方案1：缓存空对象
     * 
     * 优点：
     * 1. 实现简单
     * 2. 能够防止相同请求的穿透
     * 
     * 缺点：
     * 1. 占用额外内存
     * 2. 可能造成短期数据不一致
     */
    public Product getProduct_CacheNull(Long productId) {
        String cacheKey = "product:" + productId;
        
        // 1. 查缓存
        if (redisTemplate.hasKey(cacheKey)) {
            Product product = (Product) redisTemplate.opsForValue().get(cacheKey);
            if (product != null && product.getId() != null) {
                System.out.println("缓存命中: " + productId);
                return product;
            } else {
                // 缓存的是空对象
                System.out.println("缓存命中（空对象）: " + productId);
                return null;
            }
        }
        
        // 2. 缓存未命中，查数据库
        System.out.println("缓存未命中，查询数据库: " + productId);
        Product product = queryFromDatabase(productId);
        
        // 3. 写入缓存
        if (product != null) {
            // 正常数据，缓存1小时
            redisTemplate.opsForValue().set(cacheKey, product, 1, TimeUnit.HOURS);
        } else {
            // 空对象，缓存5分钟（较短时间）
            Product emptyProduct = new Product();
            redisTemplate.opsForValue().set(cacheKey, emptyProduct, 5, TimeUnit.MINUTES);
            System.out.println("缓存空对象: " + productId);
        }
        
        return product;
    }
    
    /**
     * 解决方案2：布隆过滤器
     * 
     * 优点：
     * 1. 内存占用少
     * 2. 查询效率高
     * 
     * 缺点：
     * 1. 存在误判（可能把不存在的判断为存在）
     * 2. 无法删除元素
     */
    public Product getProduct_BloomFilter(Long productId) {
        String cacheKey = "product:" + productId;
        
        // 1. 布隆过滤器判断
        if (!productBloomFilter.mightContain(productId)) {
            // 一定不存在，直接返回
            System.out.println("布隆过滤器判断：商品不存在 " + productId);
            return null;
        }
        
        System.out.println("布隆过滤器判断：商品可能存在 " + productId);
        
        // 2. 查缓存
        Product product = (Product) redisTemplate.opsForValue().get(cacheKey);
        if (product != null) {
            System.out.println("缓存命中: " + productId);
            return product;
        }
        
        // 3. 缓存未命中，查数据库
        System.out.println("缓存未命中，查询数据库: " + productId);
        product = queryFromDatabase(productId);
        
        // 4. 写入缓存
        if (product != null) {
            redisTemplate.opsForValue().set(cacheKey, product, 1, TimeUnit.HOURS);
        }
        
        return product;
    }
    
    /**
     * 解决方案3：组合方案（布隆过滤器 + 缓存空对象）
     * 
     * 最佳实践：
     * 1. 先用布隆过滤器快速判断
     * 2. 对于误判的情况，用缓存空对象兜底
     */
    public Product getProduct_Combined(Long productId) {
        String cacheKey = "product:" + productId;
        
        // 1. 布隆过滤器判断
        if (!productBloomFilter.mightContain(productId)) {
            System.out.println("布隆过滤器判断：商品不存在 " + productId);
            return null;
        }
        
        // 2. 查缓存（包括空对象）
        if (redisTemplate.hasKey(cacheKey)) {
            Product product = (Product) redisTemplate.opsForValue().get(cacheKey);
            if (product != null && product.getId() != null) {
                System.out.println("缓存命中: " + productId);
                return product;
            } else {
                System.out.println("缓存命中（空对象）: " + productId);
                return null;
            }
        }
        
        // 3. 缓存未命中，查数据库
        System.out.println("缓存未命中，查询数据库: " + productId);
        Product product = queryFromDatabase(productId);
        
        // 4. 写入缓存
        if (product != null) {
            redisTemplate.opsForValue().set(cacheKey, product, 1, TimeUnit.HOURS);
        } else {
            // 布隆过滤器误判，缓存空对象
            Product emptyProduct = new Product();
            redisTemplate.opsForValue().set(cacheKey, emptyProduct, 5, TimeUnit.MINUTES);
            System.out.println("布隆过滤器误判，缓存空对象: " + productId);
        }
        
        return product;
    }
    
    /**
     * 模拟数据库查询
     */
    private Product queryFromDatabase(Long productId) {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        if (productId > 0 && productId <= 1000) {
            Product product = new Product();
            product.setId(productId);
            product.setName("商品" + productId);
            product.setPrice(new BigDecimal("99.99"));
            product.setStock(100);
            return product;
        }
        
        return null;
    }
    
    /**
     * 对比测试：问题代码 vs 解决方案
     */
    public void comparePerformance() {
        System.out.println("\n=== 缓存穿透解决方案对比测试 ===\n");
        
        int attackCount = 100;
        
        // 测试1：缓存空对象方案
        System.out.println("【方案1：缓存空对象】");
        long start1 = System.currentTimeMillis();
        for (int i = 0; i < attackCount; i++) {
            getProduct_CacheNull(-1000L - i);
        }
        long time1 = System.currentTimeMillis() - start1;
        System.out.println("第一轮攻击耗时: " + time1 + "ms");
        
        // 第二轮攻击（缓存空对象已生效）
        start1 = System.currentTimeMillis();
        for (int i = 0; i < attackCount; i++) {
            getProduct_CacheNull(-1000L - i);
        }
        time1 = System.currentTimeMillis() - start1;
        System.out.println("第二轮攻击耗时: " + time1 + "ms（空对象已缓存，速度快）\n");
        
        // 测试2：布隆过滤器方案
        System.out.println("【方案2：布隆过滤器】");
        long start2 = System.currentTimeMillis();
        for (int i = 0; i < attackCount; i++) {
            getProduct_BloomFilter(-2000L - i);
        }
        long time2 = System.currentTimeMillis() - start2;
        System.out.println("攻击耗时: " + time2 + "ms（直接被过滤，速度最快）\n");
        
        System.out.println("=== 结论 ===");
        System.out.println("1. 缓存空对象：第一次会穿透，后续请求被拦截");
        System.out.println("2. 布隆过滤器：所有请求都被拦截，性能最好");
        System.out.println("3. 组合方案：兼顾两者优点，推荐使用");
    }
}
