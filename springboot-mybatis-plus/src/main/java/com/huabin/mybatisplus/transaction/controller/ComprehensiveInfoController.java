package com.huabin.mybatisplus.transaction.controller;


import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.huabin.mybatisplus.transaction.entity.ComprehensiveInfo;
import com.huabin.mybatisplus.transaction.service.IComprehensiveInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author huabin
 * @since 2023-06-02
 */
@RestController
@RequestMapping("/transaction/comprehensive-info")
public class ComprehensiveInfoController {

    @Autowired
    IComprehensiveInfoService iComprehensiveInfoService;

    @RequestMapping("/getComprehensiveInfo")
    @ResponseBody
    public List<ComprehensiveInfo> getComprehensiveInfo(){
        return iComprehensiveInfoService.getComprehensiveInfo();
    }

}
