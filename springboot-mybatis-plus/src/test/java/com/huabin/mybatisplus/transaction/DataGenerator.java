package com.huabin.mybatisplus.transaction;

import com.github.javafaker.Faker;
import com.huabin.mybatisplus.transaction.entity.SingleTable;
import com.huabin.mybatisplus.transaction.mapper.SingleTableMapper;
import com.huabin.mybatisplus.transaction.service.ISingleTableService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

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
    ISingleTableService singleTableService;

    @Test
    public void genData(){
        List<SingleTable> singleTables = new ArrayList<>();
        Faker faker = new Faker();
        for (int i = 0; i < 10000; i++) {
            SingleTable singleTable = new SingleTable();
            singleTable.setCommonField(faker.address().city());
            singleTable.setKey1(faker.beer().name());
            singleTable.setKey2(i);
            singleTable.setKey3(faker.music().key());
            singleTable.setKeyPart1(faker.company().name());
            singleTable.setKeyPart2(faker.company().industry());
            singleTable.setKeyPart3(faker.company().bs());
            singleTables.add(singleTable);
        }
        singleTableService.saveBatch(singleTables);
    }

}
