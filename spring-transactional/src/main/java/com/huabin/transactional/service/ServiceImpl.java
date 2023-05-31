package com.huabin.transactional.service;

import com.huabin.transactional.entity.Prices;
import com.huabin.transactional.mapper.PricesMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


/**
 * @Author huabin
 * @DateTime 2023-05-24 17:46
 * @Desc
 */

@Slf4j
@Service
public class ServiceImpl {

    @Autowired
    private PricesMapper pricesMapper;

//    @Transactional
    public void insert(){
        Prices prices = new Prices();
        prices.setPrice(1.0F);
        prices.setCategory("A");
        Integer insert = pricesMapper.insert(prices);
        log.info(String.valueOf(insert));
    }

}
