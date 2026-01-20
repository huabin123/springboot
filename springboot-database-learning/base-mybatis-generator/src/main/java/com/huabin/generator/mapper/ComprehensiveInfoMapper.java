package com.huabin.generator.mapper;

import com.huabin.generator.entity.ComprehensiveInfo;
import java.util.List;

/**
* Created by Mybatis Generator 2023/04/26
*/
public interface ComprehensiveInfoMapper {
    int deleteByPrimaryKey(String prodCode);

    int insert(ComprehensiveInfo record);

    ComprehensiveInfo selectByPrimaryKey(String prodCode);

    List<ComprehensiveInfo> selectAll();

    int updateByPrimaryKey(ComprehensiveInfo record);
}