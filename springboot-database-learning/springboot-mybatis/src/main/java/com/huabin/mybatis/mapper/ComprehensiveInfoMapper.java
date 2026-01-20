package com.huabin.mybatis.mapper;

import com.huabin.mybatis.entity.ComprehensiveInfo;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * Created by Mybatis Generator 2023/04/26
 * 
 * 注意：
 * 1. 已在启动类使用@MapperScan，此处的@Mapper注解可选
 * 2. 如果没有使用@MapperScan，则必须添加@Mapper注解
 * 3. 推荐使用@MapperScan统一管理，避免每个Mapper都要添加注解
 */
@Mapper  // 标记为MyBatis的Mapper接口，使其能够被Spring自动注入
public interface ComprehensiveInfoMapper {
    int deleteByPrimaryKey(String prodCode);

    int insert(ComprehensiveInfo record);

    ComprehensiveInfo selectByPrimaryKey(String prodCode);

    List<ComprehensiveInfo> selectAll();

    int updateByPrimaryKey(ComprehensiveInfo record);
}