package com.huabin.transactional.mapper;

import com.huabin.transactional.entity.Prices;
import org.springframework.stereotype.Component;

import java.util.List;

/**
* Created by Mybatis Generator 2023/05/24
*/
@Component
public interface PricesMapper {
    int deleteByPrimaryKey(Integer pid);

    int insert(Prices record);

    Prices selectByPrimaryKey(Integer pid);

    List<Prices> selectAll();

    int updateByPrimaryKey(Prices record);
}
