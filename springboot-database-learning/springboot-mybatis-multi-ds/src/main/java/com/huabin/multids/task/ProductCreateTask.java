package com.huabin.multids.task;

import com.huabin.multids.db2.entity.Product;
import com.huabin.multids.db2.entity.ProductCreateLog;
import com.huabin.multids.db2.mapper.ProductCreateLogMapper;
import com.huabin.multids.db2.mapper.ProductMapper;
import com.huabin.multids.dto.ProductCreateRequest;
import com.huabin.multids.enums.ProductCreateStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

/**
 * @Author huabin
 * @DateTime 2025-12-29
 * @Desc 产品创建任务
 * 
 * 职责：
 * 1. 执行单个产品的创建逻辑
 * 2. 记录创建日志
 * 3. 更新日志状态
 * 4. 异常处理和回滚
 * 
 * 设计说明：
 * - 实现 Runnable 接口，可以提交到线程池执行
 * - 每个任务独立处理一个产品的创建
 * - 使用 TransactionTemplate 手动管理事务
 * - 确保日志记录和产品创建在同一个事务中
 * 
 * ============================================================================
 * 【重要】为什么通过构造函数传递 Mapper 而不是使用 @Autowired 依赖注入？
 * ============================================================================
 * 
 * 1. Spring 依赖注入的前提条件：
 *    - 类必须是 Spring 管理的 Bean（使用 @Component、@Service 等注解）
 *    - 通过 Spring 容器创建实例（ApplicationContext.getBean()）
 * 
 * 2. 本类的实例化方式：
 *    - 使用 new ProductCreateTask(...) 直接创建
 *    - 不是通过 Spring 容器创建的
 *    - 因此 Spring 无法进行依赖注入
 * 
 * 3. 为什么不能用 @Component 注解？
 *    - 如果加上 @Component，这个类会变成单例 Bean
 *    - 每次执行任务时，所有线程共享同一个实例
 *    - 任务参数（request、batchNo、creator）会被覆盖，导致数据错误
 *    - 示例：
 *      线程1: new Task(产品A) -> 执行中...
 *      线程2: new Task(产品B) -> 覆盖了线程1的数据
 *      结果：线程1和线程2都处理产品B，产品A丢失
 * 
 * 4. 正确的做法（当前实现）：
 *    - 每次创建新的 Task 实例，携带不同的任务参数
 *    - 通过构造函数传递 Spring Bean（Mapper、TransactionTemplate）
 *    - 每个任务实例独立，互不干扰
 *    - 示例：
 *      线程1: new Task(产品A, mapper1, mapper2) -> 独立执行
 *      线程2: new Task(产品B, mapper1, mapper2) -> 独立执行
 *      结果：两个任务并行执行，数据正确
 * 
 * 5. 其他可行方案（不推荐）：
 *    方案A：使用 @Scope("prototype") + @Autowired
 *      - 每次都创建新实例
 *      - 但需要通过 ApplicationContext.getBean() 获取
 *      - 代码复杂度高，不如直接 new
 *    
 *    方案B：使用 @Async 注解
 *      - 方法级别的异步，不是任务级别
 *      - 无法精确控制线程池
 *      - 不适合批量任务场景
 * 
 * 6. 总结：
 *    - 任务类（Task）：普通 Java 类，通过 new 创建，参数通过构造函数传递
 *    - 服务类（Service）：Spring Bean，使用 @Autowired 注入依赖
 *    - 这是异步任务的标准设计模式
 * 
 * ============================================================================
 */
public class ProductCreateTask implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(ProductCreateTask.class);

    /**
     * 产品创建请求（任务参数，每个任务实例不同）
     */
    private final ProductCreateRequest request;

    /**
     * 批次号（任务参数，每个任务实例不同）
     */
    private final String batchNo;

    /**
     * 创建人（任务参数，每个任务实例不同）
     */
    private final String creator;

    /**
     * 产品 Mapper（Spring Bean，所有任务共享）
     * 
     * 注意：
     * - Mapper 是线程安全的（MyBatis 保证）
     * - 所有任务可以共享同一个 Mapper 实例
     * - 通过构造函数传递，而不是 @Autowired 注入
     */
    private final ProductMapper productMapper;

    /**
     * 日志 Mapper（Spring Bean，所有任务共享）
     * 
     * 注意：
     * - Mapper 是线程安全的（MyBatis 保证）
     * - 所有任务可以共享同一个 Mapper 实例
     * - 通过构造函数传递，而不是 @Autowired 注入
     */
    private final ProductCreateLogMapper logMapper;

    /**
     * 事务模板（Spring Bean，所有任务共享）
     * 
     * 注意：
     * - TransactionTemplate 是线程安全的
     * - 每次调用 execute() 都会创建新的事务
     * - 所有任务可以共享同一个 TransactionTemplate 实例
     * - 通过构造函数传递，而不是 @Autowired 注入
     */
    private final TransactionTemplate transactionTemplate;

    /**
     * 构造函数
     * 
     * 参数说明：
     * @param request 产品创建请求（任务特定参数）
     * @param batchNo 批次号（任务特定参数）
     * @param creator 创建人（任务特定参数）
     * @param productMapper 产品Mapper（Spring Bean，从Service传递）
     * @param logMapper 日志Mapper（Spring Bean，从Service传递）
     * @param transactionTemplate 事务模板（Spring Bean，从Service传递）
     * 
     * 设计原则：
     * - 任务特定参数：每个任务实例不同，直接传递
     * - Spring Bean：所有任务共享，从Service层传递
     * - 这样既保证了任务的独立性，又避免了重复创建Bean
     */
    public ProductCreateTask(ProductCreateRequest request,
                            String batchNo,
                            String creator,
                            ProductMapper productMapper,
                            ProductCreateLogMapper logMapper,
                            TransactionTemplate transactionTemplate) {
        this.request = request;
        this.batchNo = batchNo;
        this.creator = creator;
        this.productMapper = productMapper;
        this.logMapper = logMapper;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public void run() {
        Long logId = null;
        
        try {
            logger.info("开始创建产品，批次号: {}, 产品编码: {}", batchNo, request.getProductCode());
            
            // 1. 先创建日志记录（状态：创建中）
            logId = createLog();
            
            // 2. 创建产品（在事务中执行）
            final Long finalLogId = logId;
            transactionTemplate.execute(status -> {
                try {
                    // 2.1 创建产品
                    Product product = buildProduct();
                    productMapper.insert(product);
                    
                    logger.info("产品创建成功，产品ID: {}, 产品编码: {}", 
                               product.getId(), product.getProductCode());
                    
                    // 2.2 更新日志状态为成功
                    updateLogStatus(finalLogId, product.getId(), 
                                   ProductCreateStatus.SUCCESS, null);
                    
                    return product.getId();
                    
                } catch (Exception e) {
                    logger.error("产品创建失败，产品编码: {}, 错误: {}", 
                                request.getProductCode(), e.getMessage(), e);
                    
                    // 更新日志状态为失败
                    updateLogStatus(finalLogId, null, 
                                   ProductCreateStatus.FAILED, e.getMessage());
                    
                    // 抛出异常，触发事务回滚
                    throw new RuntimeException("产品创建失败: " + e.getMessage(), e);
                }
            });
            
        } catch (Exception e) {
            logger.error("产品创建任务执行失败，批次号: {}, 产品编码: {}, 错误: {}", 
                        batchNo, request.getProductCode(), e.getMessage(), e);
            
            // 如果日志创建失败，尝试再次创建失败日志
            if (logId == null) {
                try {
                    createFailedLog(e.getMessage());
                } catch (Exception ex) {
                    logger.error("创建失败日志失败: {}", ex.getMessage(), ex);
                }
            }
        }
    }

    /**
     * 创建日志记录（状态：创建中）
     */
    private Long createLog() {
        ProductCreateLog log = new ProductCreateLog();
        log.setBatchNo(batchNo);
        log.setProductCode(request.getProductCode());
        log.setProductName(request.getProductName());
        log.setPrice(request.getPrice());
        log.setStock(request.getStock());
        log.setDescription(request.getDescription());
        log.setCreateStatus(ProductCreateStatus.CREATING.getCode());
        log.setCreator(creator);
        
        logMapper.insert(log);
        
        logger.info("创建日志成功，日志ID: {}, 产品编码: {}", log.getId(), request.getProductCode());
        
        return log.getId();
    }

    /**
     * 创建失败日志（直接创建失败状态的日志）
     */
    private void createFailedLog(String errorMessage) {
        ProductCreateLog log = new ProductCreateLog();
        log.setBatchNo(batchNo);
        log.setProductCode(request.getProductCode());
        log.setProductName(request.getProductName());
        log.setPrice(request.getPrice());
        log.setStock(request.getStock());
        log.setDescription(request.getDescription());
        log.setCreateStatus(ProductCreateStatus.FAILED.getCode());
        log.setErrorMessage(truncateErrorMessage(errorMessage));
        log.setCreator(creator);
        
        logMapper.insert(log);
    }

    /**
     * 更新日志状态
     */
    private void updateLogStatus(Long logId, Long productId, 
                                 ProductCreateStatus status, String errorMessage) {
        try {
            logMapper.updateStatus(logId, productId, status.getCode(), 
                                  truncateErrorMessage(errorMessage));
            
            logger.info("更新日志状态成功，日志ID: {}, 状态: {}", logId, status.getDesc());
            
        } catch (Exception e) {
            logger.error("更新日志状态失败，日志ID: {}, 错误: {}", logId, e.getMessage(), e);
        }
    }

    /**
     * 构建产品对象
     */
    private Product buildProduct() {
        Product product = new Product();
        product.setProductName(request.getProductName());
        product.setProductCode(request.getProductCode());
        product.setPrice(request.getPrice());
        product.setStock(request.getStock());
        product.setDescription(request.getDescription());
        product.setStatus(1); // 默认上架
        product.setCreateTime(LocalDateTime.now());
        product.setUpdateTime(LocalDateTime.now());
        
        return product;
    }

    /**
     * 截断错误信息（避免超过数据库字段长度）
     */
    private String truncateErrorMessage(String errorMessage) {
        if (errorMessage == null) {
            return null;
        }
        
        int maxLength = 500;
        if (errorMessage.length() > maxLength) {
            return errorMessage.substring(0, maxLength - 3) + "...";
        }
        
        return errorMessage;
    }
}
