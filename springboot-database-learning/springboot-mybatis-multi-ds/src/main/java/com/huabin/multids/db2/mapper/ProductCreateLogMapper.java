package com.huabin.multids.db2.mapper;

import com.huabin.multids.db2.entity.ProductCreateLog;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @Author huabin
 * @DateTime 2025-12-29
 * @Desc 产品创建日志 Mapper
 */
public interface ProductCreateLogMapper {

    /**
     * 插入日志
     */
    int insert(ProductCreateLog log);

    /**
     * 根据ID查询
     */
    ProductCreateLog selectByPrimaryKey(Long id);

    /**
     * 根据批次号查询
     */
    List<ProductCreateLog> selectByBatchNo(@Param("batchNo") String batchNo);

    /**
     * 更新日志状态
     */
    int updateStatus(@Param("id") Long id, 
                    @Param("productId") Long productId,
                    @Param("createStatus") Integer createStatus, 
                    @Param("errorMessage") String errorMessage);

    /**
     * 查询所有日志
     */
    List<ProductCreateLog> selectAll();

    /**
     * 根据创建人查询
     */
    List<ProductCreateLog> selectByCreator(@Param("creator") String creator);
}
