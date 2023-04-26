package com.huabin.mybatis.mapper;

import com.huabin.mybatis.entity.ComprehensiveInfo;
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