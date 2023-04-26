package com.huabin.generator.controller;

import com.huabin.generator.service.ComprehensiveInfoService;
import com.huabin.generator.service.impl.ComprehensiveInfoServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * @Author huabin
 * @DateTime 2023-04-25 17:48
 * @Desc
 */

@RestController
public class ComprehensiveInfoController {

    @Autowired
    ComprehensiveInfoServiceImpl comprehensiveInfoService;

    @GetMapping(value = "/test")
    public String getComprehensiveInfo(@RequestParam(value = "prodCode") String prodCode) {
        return comprehensiveInfoService.selectComprehensiveInfoByProdCode(prodCode);
    }

}
