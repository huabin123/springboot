package com.huabin.transaction.forupdate.mapper;

import com.huabin.transaction.forupdate.entity.Product;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

/**
 * 商品Mapper接口
 * 
 * @author huabin
 */
public interface ProductMapper {
    
    /**
     * 根据ID查询商品（普通查询，快照读）
     */
    Product selectById(@Param("id") Long id);
    
    /**
     * 根据ID查询商品（加排他锁 FOR UPDATE，当前读）
     */
    Product selectByIdForUpdate(@Param("id") Long id);
    
    /**
     * 根据ID查询商品（加共享锁 LOCK IN SHARE MODE，当前读）
     */
    Product selectByIdLockInShareMode(@Param("id") Long id);
    
    /**
     * 更新商品价格
     */
    int updatePrice(@Param("id") Long id, @Param("price") BigDecimal price);
    
    /**
     * 扣减库存
     */
    int deductStock(@Param("id") Long id, @Param("quantity") Integer quantity);
    
    /**
     * 增加库存
     */
    int addStock(@Param("id") Long id, @Param("quantity") Integer quantity);
    
    /**
     * 插入商品
     */
    int insert(Product product);
    
    /**
     * 删除所有商品（测试用）
     */
    int deleteAll();
    
    /**
     * 初始化测试数据
     */
    int initTestData();
}
