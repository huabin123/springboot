package com.huabin.multids.service;

import com.huabin.multids.db2.entity.Product;
import com.huabin.multids.db2.mapper.ProductMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @Author huabin
 * @DateTime 2025-12-29
 * @Desc 产品业务逻辑层（使用从数据源 springboot_db2）
 * 
 * 关键说明：
 * 1. 注入的是 db2.mapper.ProductMapper，自动使用从数据源
 * 2. @Transactional 必须明确指定事务管理器：transactionManager = "secondaryTransactionManager"
 * 3. 如果不指定，会使用默认的主数据源事务管理器，导致事务失效
 */
@Service
public class ProductService {

    /**
     * 注入从数据源的 ProductMapper
     * 
     * 说明：
     * - 由于 ProductMapper 在 com.huabin.multids.db2.mapper 包下
     * - SecondaryDataSourceConfig 配置了扫描此包
     * - 因此自动使用从数据源（springboot_db2）
     */
    @Autowired
    private ProductMapper productMapper;

    /**
     * 根据ID查询产品
     */
    public Product getProductById(Long id) {
        return productMapper.selectByPrimaryKey(id);
    }

    /**
     * 查询所有产品
     */
    public List<Product> getAllProducts() {
        return productMapper.selectAll();
    }

    /**
     * 根据产品编码查询
     */
    public Product getProductByCode(String productCode) {
        return productMapper.selectByProductCode(productCode);
    }

    /**
     * 根据条件查询产品列表
     */
    public List<Product> getProductsByCondition(String productName, Integer status) {
        return productMapper.selectByCondition(productName, status);
    }

    /**
     * 创建产品（带事务）
     * 
     * 重要：
     * - 必须指定 transactionManager = "secondaryTransactionManager"
     * - 如果不指定，会使用默认的 primaryTransactionManager
     * - 这会导致事务管理器与数据源不匹配，事务失效
     */
    @Transactional(transactionManager = "secondaryTransactionManager", rollbackFor = Exception.class)
    public int createProduct(Product product) {
        product.setCreateTime(LocalDateTime.now());
        product.setUpdateTime(LocalDateTime.now());
        return productMapper.insert(product);
    }

    /**
     * 更新产品（带事务）
     */
    @Transactional(transactionManager = "secondaryTransactionManager", rollbackFor = Exception.class)
    public int updateProduct(Product product) {
        product.setUpdateTime(LocalDateTime.now());
        return productMapper.updateByPrimaryKey(product);
    }

    /**
     * 删除产品（带事务）
     */
    @Transactional(transactionManager = "secondaryTransactionManager", rollbackFor = Exception.class)
    public int deleteProduct(Long id) {
        return productMapper.deleteByPrimaryKey(id);
    }

    /**
     * 批量创建产品（带事务）
     * 
     * 说明：
     * - 整个方法在一个事务中执行
     * - 任何一个产品创建失败，所有操作都会回滚
     * - 必须使用 secondaryTransactionManager
     */
    @Transactional(transactionManager = "secondaryTransactionManager", rollbackFor = Exception.class)
    public int batchCreateProducts(List<Product> products) {
        int count = 0;
        for (Product product : products) {
            product.setCreateTime(LocalDateTime.now());
            product.setUpdateTime(LocalDateTime.now());
            count += productMapper.insert(product);
        }
        return count;
    }
}
