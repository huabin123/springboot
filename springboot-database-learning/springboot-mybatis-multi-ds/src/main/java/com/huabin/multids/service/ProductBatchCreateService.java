package com.huabin.multids.service;

import com.huabin.multids.db2.entity.ProductCreateLog;
import com.huabin.multids.db2.mapper.ProductCreateLogMapper;
import com.huabin.multids.db2.mapper.ProductMapper;
import com.huabin.multids.dto.BatchCreateRequest;
import com.huabin.multids.dto.ProductCreateRequest;
import com.huabin.multids.task.ProductCreateTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @Author huabin
 * @DateTime 2025-12-29
 * @Desc 产品批量创建服务
 * 
 * 职责：
 * 1. 接收批量创建请求
 * 2. 生成批次号
 * 3. 提交任务到线程池
 * 4. 快速返回批次号
 * 5. 提供查询批次状态的方法
 * 
 * 设计说明：
 * - 接口调用后立即返回，不等待任务完成
 * - 每个产品创建任务独立执行，互不影响
 * - 使用线程池控制并发数量
 * - 通过批次号查询任务执行状态
 */
@Service
public class ProductBatchCreateService {

    private static final Logger logger = LoggerFactory.getLogger(ProductBatchCreateService.class);

    /**
     * 产品 Mapper（从数据源）
     */
    @Autowired
    private ProductMapper productMapper;

    /**
     * 日志 Mapper（从数据源）
     */
    @Autowired
    private ProductCreateLogMapper logMapper;

    /**
     * 产品创建线程池
     */
    @Autowired
    @Qualifier("productCreateExecutor")
    private ThreadPoolExecutor productCreateExecutor;

    /**
     * 从数据源事务模板
     */
    @Autowired
    @Qualifier("secondaryTransactionTemplate")
    private TransactionTemplate transactionTemplate;

    /**
     * 批次号计数器（用于生成唯一批次号）
     */
    private static final AtomicInteger BATCH_COUNTER = new AtomicInteger(0);

    /**
     * 批量创建产品（异步）
     * 
     * @param request 批量创建请求
     * @return 批次号
     */
    public String batchCreateProducts(BatchCreateRequest request) {
        // 1. 参数校验
        validateRequest(request);
        
        // 2. 生成批次号
        String batchNo = generateBatchNo();
        
        logger.info("========================================");
        logger.info("开始批量创建产品");
        logger.info("批次号: {}", batchNo);
        logger.info("创建人: {}", request.getCreator());
        logger.info("产品数量: {}", request.getProducts().size());
        logger.info("========================================");
        
        // 3. 提交任务到线程池
        List<ProductCreateRequest> products = request.getProducts();
        int successCount = 0;
        int failedCount = 0;
        
        for (ProductCreateRequest product : products) {
            try {
                ProductCreateTask task = new ProductCreateTask(
                    product,
                    batchNo,
                    request.getCreator(),
                    productMapper,
                    logMapper,
                    transactionTemplate
                );
                
                // 提交到线程池异步执行
                productCreateExecutor.execute(task);
                successCount++;
                
                logger.debug("提交产品创建任务成功，产品编码: {}", product.getProductCode());
                
            } catch (RejectedExecutionException e) {
                // 线程池拒绝任务，记录失败日志
                failedCount++;
                logger.error("线程池拒绝任务，产品编码: {}, 原因: 线程池队列已满", product.getProductCode());
                
                // 记录失败日志到数据库
                recordRejectedTask(product, batchNo, request.getCreator(), e);
            }
        }
        
        logger.info("批量创建任务提交完成，批次号: {}, 总数: {}, 成功: {}, 失败: {}", 
                   batchNo, products.size(), successCount, failedCount);
        
        // 4. 立即返回批次号
        return batchNo;
    }

    /**
     * 查询批次创建状态
     * 
     * @param batchNo 批次号
     * @return 批次日志列表
     */
    public List<ProductCreateLog> queryBatchStatus(String batchNo) {
        logger.info("查询批次状态，批次号: {}", batchNo);
        
        List<ProductCreateLog> logs = logMapper.selectByBatchNo(batchNo);
        
        logger.info("查询批次状态完成，批次号: {}, 记录数: {}", batchNo, logs.size());
        
        return logs;
    }

    /**
     * 查询创建人的所有批次
     * 
     * @param creator 创建人
     * @return 批次日志列表
     */
    public List<ProductCreateLog> queryByCreator(String creator) {
        logger.info("查询创建人的批次，创建人: {}", creator);
        
        List<ProductCreateLog> logs = logMapper.selectByCreator(creator);
        
        logger.info("查询创建人批次完成，创建人: {}, 记录数: {}", creator, logs.size());
        
        return logs;
    }

    /**
     * 参数校验
     */
    private void validateRequest(BatchCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求参数不能为空");
        }
        
        if (request.getCreator() == null || request.getCreator().trim().isEmpty()) {
            throw new IllegalArgumentException("创建人不能为空");
        }
        
        if (request.getProducts() == null || request.getProducts().isEmpty()) {
            throw new IllegalArgumentException("产品列表不能为空");
        }
        
        // 限制单次批量创建数量（避免任务过多）
        int maxBatchSize = 1000;
        if (request.getProducts().size() > maxBatchSize) {
            throw new IllegalArgumentException("单次批量创建数量不能超过 " + maxBatchSize);
        }
        
        // 校验每个产品
        for (int i = 0; i < request.getProducts().size(); i++) {
            ProductCreateRequest product = request.getProducts().get(i);
            validateProduct(product, i);
        }
    }

    /**
     * 校验单个产品
     */
    private void validateProduct(ProductCreateRequest product, int index) {
        if (product == null) {
            throw new IllegalArgumentException("第 " + (index + 1) + " 个产品不能为空");
        }
        
        if (product.getProductCode() == null || product.getProductCode().trim().isEmpty()) {
            throw new IllegalArgumentException("第 " + (index + 1) + " 个产品编码不能为空");
        }
        
        if (product.getProductName() == null || product.getProductName().trim().isEmpty()) {
            throw new IllegalArgumentException("第 " + (index + 1) + " 个产品名称不能为空");
        }
        
        if (product.getPrice() == null || product.getPrice().doubleValue() <= 0) {
            throw new IllegalArgumentException("第 " + (index + 1) + " 个产品价格必须大于0");
        }
        
        if (product.getStock() == null || product.getStock() < 0) {
            throw new IllegalArgumentException("第 " + (index + 1) + " 个产品库存不能为负数");
        }
    }

    /**
     * 生成批次号
     * 
     * 格式：BATCH_yyyyMMddHHmmss_序号
     * 示例：BATCH_20251229193000_001
     */
    private String generateBatchNo() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int counter = BATCH_COUNTER.incrementAndGet();
        
        // 序号循环（避免无限增长）
        if (counter > 999999) {
            BATCH_COUNTER.set(0);
            counter = BATCH_COUNTER.incrementAndGet();
        }
        
        return String.format("BATCH_%s_%06d", timestamp, counter);
    }

    /**
     * 记录被拒绝的任务
     * 
     * 当线程池队列满时，任务会被拒绝，此时需要记录失败日志
     * 
     * @param product 产品信息
     * @param batchNo 批次号
     * @param creator 创建人
     * @param e 拒绝异常
     */
    private void recordRejectedTask(ProductCreateRequest product, String batchNo, 
                                   String creator, RejectedExecutionException e) {
        try {
            // 使用事务模板记录失败日志
            transactionTemplate.execute(status -> {
                ProductCreateLog log = new ProductCreateLog();
                log.setBatchNo(batchNo);
                log.setProductCode(product.getProductCode());
                log.setProductName(product.getProductName());
                log.setPrice(product.getPrice());
                log.setStock(product.getStock());
                log.setDescription(product.getDescription());
                log.setCreateStatus(2); // 2-创建失败
                log.setErrorMessage("线程池队列已满，任务被拒绝: " + e.getMessage());
                log.setCreator(creator);
                log.setCreateTime(LocalDateTime.now());
                
                logMapper.insert(log);
                
                logger.info("记录被拒绝任务的失败日志，产品编码: {}, 批次号: {}", 
                           product.getProductCode(), batchNo);
                
                return null;
            });
        } catch (Exception ex) {
            // 记录日志失败，只打印错误，不影响主流程
            logger.error("记录被拒绝任务的失败日志时发生异常，产品编码: {}, 错误: {}", 
                        product.getProductCode(), ex.getMessage(), ex);
        }
    }
}
