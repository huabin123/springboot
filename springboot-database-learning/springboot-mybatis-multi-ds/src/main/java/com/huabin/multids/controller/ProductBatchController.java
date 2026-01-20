package com.huabin.multids.controller;

import com.huabin.multids.db2.entity.ProductCreateLog;
import com.huabin.multids.dto.BatchCreateRequest;
import com.huabin.multids.enums.ProductCreateStatus;
import com.huabin.multids.service.ProductBatchCreateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Author huabin
 * @DateTime 2025-12-29
 * @Desc 产品批量创建控制器
 * 
 * API接口：
 * POST   /api/products/batch          - 批量创建产品（异步）
 * GET    /api/products/batch/{batchNo} - 查询批次状态
 * GET    /api/products/batch/creator/{creator} - 查询创建人的所有批次
 */
@RestController
@RequestMapping("/api/products/batch")
public class ProductBatchController {

    @Autowired
    private ProductBatchCreateService batchCreateService;

    /**
     * 批量创建产品（异步）
     * 
     * 说明：
     * 1. 接口调用后立即返回批次号
     * 2. 产品创建任务在线程池中异步执行
     * 3. 通过批次号查询任务执行状态
     * 
     * 测试命令：
     * curl -X POST http://localhost:8080/api/products/batch \
     *   -H "Content-Type: application/json" \
     *   -d '{
     *     "creator": "zhangsan",
     *     "products": [
     *       {
     *         "productName": "iPhone 15",
     *         "productCode": "IP15001",
     *         "price": 5999.00,
     *         "stock": 100,
     *         "description": "最新款iPhone"
     *       },
     *       {
     *         "productName": "MacBook Pro",
     *         "productCode": "MBP001",
     *         "price": 12999.00,
     *         "stock": 50,
     *         "description": "专业笔记本电脑"
     *       }
     *     ]
     *   }'
     */
    @PostMapping
    public Map<String, Object> batchCreate(@RequestBody BatchCreateRequest request) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 提交批量创建任务
            String batchNo = batchCreateService.batchCreateProducts(request);
            
            result.put("success", true);
            result.put("batchNo", batchNo);
            result.put("message", "批量创建任务已提交，请通过批次号查询执行状态");
            result.put("totalCount", request.getProducts().size());
            result.put("queryUrl", "/api/products/batch/" + batchNo);
            
        } catch (IllegalArgumentException e) {
            // 参数校验失败
            result.put("success", false);
            result.put("message", "参数校验失败：" + e.getMessage());
            
        } catch (Exception e) {
            // 其他异常
            result.put("success", false);
            result.put("message", "批量创建任务提交失败：" + e.getMessage());
        }
        
        return result;
    }

    /**
     * 查询批次状态
     * 
     * 说明：
     * 1. 返回批次中所有产品的创建状态
     * 2. 状态：0-创建中，1-创建成功，2-创建失败
     * 3. 可以轮询此接口查询任务进度
     * 
     * 测试命令：
     * curl http://localhost:8080/api/products/batch/BATCH_20251229193000_000001
     */
    @GetMapping("/{batchNo}")
    public Map<String, Object> queryBatchStatus(@PathVariable String batchNo) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            List<ProductCreateLog> logs = batchCreateService.queryBatchStatus(batchNo);
            
            if (logs.isEmpty()) {
                result.put("success", false);
                result.put("message", "批次不存在");
                return result;
            }
            
            // 统计各状态数量
            int totalCount = logs.size();
            int creatingCount = 0;
            int successCount = 0;
            int failedCount = 0;
            
            for (ProductCreateLog log : logs) {
                Integer status = log.getCreateStatus();
                if (ProductCreateStatus.CREATING.getCode().equals(status)) {
                    creatingCount++;
                } else if (ProductCreateStatus.SUCCESS.getCode().equals(status)) {
                    successCount++;
                } else if (ProductCreateStatus.FAILED.getCode().equals(status)) {
                    failedCount++;
                }
            }
            
            // 判断批次是否完成
            boolean isCompleted = (creatingCount == 0);
            
            result.put("success", true);
            result.put("batchNo", batchNo);
            result.put("isCompleted", isCompleted);
            result.put("totalCount", totalCount);
            result.put("creatingCount", creatingCount);
            result.put("successCount", successCount);
            result.put("failedCount", failedCount);
            result.put("data", logs);
            
            if (isCompleted) {
                result.put("message", "批次执行完成");
            } else {
                result.put("message", "批次执行中，请稍后查询");
            }
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "查询失败：" + e.getMessage());
        }
        
        return result;
    }

    /**
     * 查询创建人的所有批次
     * 
     * 测试命令：
     * curl http://localhost:8080/api/products/batch/creator/zhangsan
     */
    @GetMapping("/creator/{creator}")
    public Map<String, Object> queryByCreator(@PathVariable String creator) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            List<ProductCreateLog> logs = batchCreateService.queryByCreator(creator);
            
            result.put("success", true);
            result.put("creator", creator);
            result.put("totalCount", logs.size());
            result.put("data", logs);
            result.put("message", "查询成功");
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "查询失败：" + e.getMessage());
        }
        
        return result;
    }

    /**
     * 健康检查
     * 
     * 测试命令：
     * curl http://localhost:8080/api/products/batch/health
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "产品批量创建服务正常");
        result.put("service", "ProductBatchCreateService");
        result.put("datasource", "secondary (springboot_db2)");
        return result;
    }
}
