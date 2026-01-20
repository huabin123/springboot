package com.huabin.multids.db2.mapper;

import com.huabin.multids.db2.entity.Product;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @Author huabin
 * @DateTime 2025-12-29
 * @Desc 产品Mapper接口（从数据源 springboot_db2）
 * 
 * 说明：
 * - 此接口为示例代码，实际使用时应通过 MyBatis Generator 生成
 * - 对应XML文件：src/main/resources/mapper/db2/ProductMapper.xml
 * - 数据源配置：SecondaryDataSourceConfig
 */
public interface ProductMapper {

    /**
     * 根据主键删除
     */
    int deleteByPrimaryKey(Long id);

    /**
     * 插入记录
     */
    int insert(Product record);

    /**
     * 根据主键查询
     */
    Product selectByPrimaryKey(Long id);

    /**
     * 查询所有产品
     */
    List<Product> selectAll();

    /**
     * 根据产品编码查询
     */
    Product selectByProductCode(@Param("productCode") String productCode);

    /**
     * 根据主键更新
     */
    int updateByPrimaryKey(Product record);

    /**
     * 根据条件查询产品列表
     */
    List<Product> selectByCondition(@Param("productName") String productName, 
                                     @Param("status") Integer status);
}
