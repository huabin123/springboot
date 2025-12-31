package com.huabin.multids.controller;

import com.huabin.multids.db2.entity.Product;
import com.huabin.multids.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Author huabin
 * @DateTime 2025-12-29
 * @Desc 产品控制器（从数据源 springboot_db2）
 * 
 * API接口：
 * GET    /api/products/{id}     - 根据ID查询产品
 * GET    /api/products          - 查询所有产品
 * GET    /api/products/search   - 根据条件查询产品
 * POST   /api/products          - 创建产品
 * PUT    /api/products          - 更新产品
 * DELETE /api/products/{id}     - 删除产品
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    @Autowired
    private ProductService productService;

    /**
     * 根据ID查询产品
     * 
     * 测试命令：
     * curl http://localhost:8080/api/products/1
     */
    @GetMapping("/{id}")
    public Map<String, Object> getProductById(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        try {
            Product product = productService.getProductById(id);
            if (product != null) {
                result.put("success", true);
                result.put("data", product);
                result.put("message", "查询成功");
            } else {
                result.put("success", false);
                result.put("message", "产品不存在");
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "查询失败：" + e.getMessage());
        }
        return result;
    }

    /**
     * 查询所有产品
     * 
     * 测试命令：
     * curl http://localhost:8080/api/products
     */
    @GetMapping
    public Map<String, Object> getAllProducts() {
        Map<String, Object> result = new HashMap<>();
        try {
            List<Product> products = productService.getAllProducts();
            result.put("success", true);
            result.put("data", products);
            result.put("count", products.size());
            result.put("message", "查询成功");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "查询失败：" + e.getMessage());
        }
        return result;
    }

    /**
     * 根据条件查询产品
     * 
     * 测试命令：
     * curl "http://localhost:8080/api/products/search?productName=手机&status=1"
     */
    @GetMapping("/search")
    public Map<String, Object> searchProducts(
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) Integer status) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<Product> products = productService.getProductsByCondition(productName, status);
            result.put("success", true);
            result.put("data", products);
            result.put("count", products.size());
            result.put("message", "查询成功");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "查询失败：" + e.getMessage());
        }
        return result;
    }

    /**
     * 创建产品
     * 
     * 测试命令：
     * curl -X POST http://localhost:8080/api/products \
     *   -H "Content-Type: application/json" \
     *   -d '{"productName":"iPhone 15","productCode":"IP15","price":5999.00,"stock":100,"description":"最新款iPhone","status":1}'
     */
    @PostMapping
    public Map<String, Object> createProduct(@RequestBody Product product) {
        Map<String, Object> result = new HashMap<>();
        try {
            int rows = productService.createProduct(product);
            if (rows > 0) {
                result.put("success", true);
                result.put("data", product);
                result.put("message", "创建成功");
            } else {
                result.put("success", false);
                result.put("message", "创建失败");
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "创建失败：" + e.getMessage());
        }
        return result;
    }

    /**
     * 更新产品
     * 
     * 测试命令：
     * curl -X PUT http://localhost:8080/api/products \
     *   -H "Content-Type: application/json" \
     *   -d '{"id":1,"productName":"iPhone 15 Pro","productCode":"IP15P","price":6999.00,"stock":50,"description":"Pro版本","status":1}'
     */
    @PutMapping
    public Map<String, Object> updateProduct(@RequestBody Product product) {
        Map<String, Object> result = new HashMap<>();
        try {
            int rows = productService.updateProduct(product);
            if (rows > 0) {
                result.put("success", true);
                result.put("message", "更新成功");
            } else {
                result.put("success", false);
                result.put("message", "更新失败，产品不存在");
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "更新失败：" + e.getMessage());
        }
        return result;
    }

    /**
     * 删除产品
     * 
     * 测试命令：
     * curl -X DELETE http://localhost:8080/api/products/1
     */
    @DeleteMapping("/{id}")
    public Map<String, Object> deleteProduct(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        try {
            int rows = productService.deleteProduct(id);
            if (rows > 0) {
                result.put("success", true);
                result.put("message", "删除成功");
            } else {
                result.put("success", false);
                result.put("message", "删除失败，产品不存在");
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "删除失败：" + e.getMessage());
        }
        return result;
    }

    /**
     * 健康检查
     * 
     * 测试命令：
     * curl http://localhost:8080/api/products/health
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "产品服务正常（从数据源 springboot_db2）");
        result.put("datasource", "secondary");
        result.put("database", "springboot_db2");
        return result;
    }
}
