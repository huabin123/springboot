package com.huabin.generator.service;

import org.springframework.stereotype.Component;

@Component
public interface ComprehensiveInfoService {

    String selectComprehensiveInfoByProdCode(String prodCode);

}
