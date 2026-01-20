package com.huabin.mybatis.controller;

import com.huabin.mybatis.entity.ComprehensiveInfo;
import com.huabin.mybatis.service.ComprehensiveInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @Author huabin
 * @DateTime 2025-12-29
 * @Desc ComprehensiveInfo控制器
 * 
 * RESTful API示例：
 * GET    /api/comprehensive/{prodCode}  - 根据ID查询
 * GET    /api/comprehensive              - 查询所有
 * POST   /api/comprehensive              - 新增
 * PUT    /api/comprehensive              - 更新
 * DELETE /api/comprehensive/{prodCode}  - 删除
 */
@RestController
@RequestMapping("/api/comprehensive")
public class ComprehensiveInfoController {

    @Autowired
    private ComprehensiveInfoService comprehensiveInfoService;

    /**
     * 根据产品代码查询
     * 
     * 测试命令：
     * curl http://localhost:8080/api/comprehensive/PROD001
     */
    @GetMapping("/{prodCode}")
    public ComprehensiveInfo getById(@PathVariable String prodCode) {
        return comprehensiveInfoService.getById(prodCode);
    }

    /**
     * 查询所有
     * 
     * 测试命令：
     * curl http://localhost:8080/api/comprehensive
     */
    @GetMapping
    public List<ComprehensiveInfo> getAll() {
        return comprehensiveInfoService.getAll();
    }

    /**
     * 新增
     * 
     * 测试命令：
     * curl -X POST http://localhost:8080/api/comprehensive \
     *   -H "Content-Type: application/json" \
     *   -d '{"prodCode":"PROD001","prodName":"测试产品"}'
     */
    @PostMapping
    public String add(@RequestBody ComprehensiveInfo info) {
        int result = comprehensiveInfoService.add(info);
        return result > 0 ? "添加成功" : "添加失败";
    }

    /**
     * 更新
     * 
     * 测试命令：
     * curl -X PUT http://localhost:8080/api/comprehensive \
     *   -H "Content-Type: application/json" \
     *   -d '{"prodCode":"PROD001","prodName":"更新产品"}'
     */
    @PutMapping
    public String update(@RequestBody ComprehensiveInfo info) {
        int result = comprehensiveInfoService.update(info);
        return result > 0 ? "更新成功" : "更新失败";
    }

    /**
     * 删除
     * 
     * 测试命令：
     * curl -X DELETE http://localhost:8080/api/comprehensive/PROD001
     */
    @DeleteMapping("/{prodCode}")
    public String delete(@PathVariable String prodCode) {
        int result = comprehensiveInfoService.delete(prodCode);
        return result > 0 ? "删除成功" : "删除失败";
    }
}
