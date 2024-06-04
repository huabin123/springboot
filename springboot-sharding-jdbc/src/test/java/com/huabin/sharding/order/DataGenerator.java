package com.huabin.sharding.order;

import cn.hutool.core.lang.generator.UUIDGenerator;
import cn.hutool.core.util.IdUtil;
import com.github.javafaker.Faker;
import com.huabin.sharding.order.entity.OrderInfo;
import com.huabin.sharding.order.service.IOrderInfoService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * @Author huabin
 * @DateTime 2023-06-09 10:02
 * @Desc
 */
@SpringBootTest
@RunWith(SpringRunner.class)
public class DataGenerator {

    @Autowired
    IOrderInfoService iOrderInfoService;

    @Test
    public void genData(){
        List<OrderInfo> orders = new ArrayList<>();
        Faker faker = new Faker();
        for (int i = 1; i <= 40000000; i++) {
            OrderInfo order = new OrderInfo();
            String idStr = IdUtil.getSnowflakeNextIdStr();
            order.setOrderNo(idStr);
            order.setCreateUser("hb");
            order.setOrderTime(LocalDateTime.now());
            order.setMobile(String.valueOf(faker.number().randomNumber(11, true)));
            order.setStatus(1);
            order.setPayTime(LocalDateTime.now().plusSeconds(15));
            order.setCreateDate(LocalDateTime.now());
            order.setId(idStr);
            orders.add(order);
            if (i % 10000 == 0) {
                System.out.println(i);
                iOrderInfoService.saveBatch(orders);
                orders.clear();
            }
        }
    }
}
