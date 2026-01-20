package com.huabin.transaction.mapper;

import com.huabin.transaction.entity.Orders;
import org.apache.ibatis.annotations.Param;

/**
 * 订单Mapper
 * 
 * @author huabin
 */
public interface OrdersMapper {
    
    /**
     * 插入订单
     */
    int insert(Orders orders);
    
    /**
     * 根据ID查询
     */
    Orders selectById(@Param("id") Long id);
    
    /**
     * 更新订单状态
     */
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);
}
