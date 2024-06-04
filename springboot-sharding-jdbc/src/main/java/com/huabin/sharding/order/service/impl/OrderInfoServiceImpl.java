package com.huabin.sharding.order.service.impl;

import com.huabin.sharding.order.entity.OrderInfo;
import com.huabin.sharding.order.mapper.OrderInfoMapper;
import com.huabin.sharding.order.service.IOrderInfoService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 订单表 服务实现类
 * </p>
 *
 * @author huabin
 * @since 2024-05-30
 */
@Service
public class OrderInfoServiceImpl extends ServiceImpl<OrderInfoMapper, OrderInfo> implements IOrderInfoService {

}
