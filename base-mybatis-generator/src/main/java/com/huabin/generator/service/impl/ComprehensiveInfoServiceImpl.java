package com.huabin.generator.service.impl;

import com.huabin.generator.mapper.ComprehensiveInfoMapper;
import com.huabin.generator.service.ComprehensiveInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @Author huabin
 * @DateTime 2023-04-25 17:56
 * @Desc
 */

@Service
public class ComprehensiveInfoServiceImpl implements ComprehensiveInfoService {

    @Autowired
    ComprehensiveInfoMapper comprehensiveInfoMapper;

    @Override
    public String selectComprehensiveInfoByProdCode(String prodCode) {
        return comprehensiveInfoMapper.selectByPrimaryKey(prodCode).getProdName();
    }

}
