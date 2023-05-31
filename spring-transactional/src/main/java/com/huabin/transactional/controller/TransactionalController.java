package com.huabin.transactional.controller;

import com.huabin.transactional.service.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author huabin
 * @DateTime 2023-05-24 18:35
 * @Desc
 */
@RestController
@RequestMapping("/tran")
public class TransactionalController {

    @Autowired
    private ServiceImpl serviceImpl;

    @PostMapping("/insert")
    public void insert(){
        serviceImpl.insert();
    }

}
