package com.huabin.excel.export.controller;

import com.huabin.excel.export.service.ExcelExportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @Author huabin
 * @DateTime 2025-12-30
 * @Desc Excel 导出控制器
 * 
 * 职责：
 * 1. 接收 HTTP 请求
 * 2. 参数解析
 * 3. 调用 Service 层
 * 4. 异常处理
 * 
 * 接口说明：
 * - GET /api/excel/export - 基础导出接口
 * - GET /api/excel/export/large - 大数据量导出测试接口
 */
@RestController
@RequestMapping("/api/excel")
public class ExcelExportController {

    private static final Logger logger = LoggerFactory.getLogger(ExcelExportController.class);

    @Autowired
    private ExcelExportService excelExportService;

    /**
     * 导出 Excel
     * 
     * 接口地址：GET /api/excel/export
     * 
     * 参数说明：
     * - count: 数据条数（可选，默认1000）
     * - fileName: 文件名（可选，默认自动生成）
     * - sheetName: Sheet名称（可选，默认"数据导出"）
     * 
     * 示例：
     * http://localhost:8080/api/excel/export?count=5000
     * http://localhost:8080/api/excel/export?count=5000&fileName=订单数据&sheetName=订单列表
     * 
     * 响应头说明：
     * - X-Total-Count: 数据总条数
     * - X-Export-Count: 实际导出条数
     * - X-Data-Overflow: 数据超长警告信息（仅当数据超过10万条时返回）
     * 
     * @param response  HTTP 响应对象
     * @param count     数据条数
     * @param fileName  文件名
     * @param sheetName Sheet 名称
     */
    @GetMapping("/export")
    public void export(HttpServletResponse response,
                      @RequestParam(value = "count", required = false) Integer count,
                      @RequestParam(value = "fileName", required = false) String fileName,
                      @RequestParam(value = "sheetName", required = false) String sheetName) {
        
        logger.info("收到 Excel 导出请求，参数: count={}, fileName={}, sheetName={}", 
                   count, fileName, sheetName);

        try {
            excelExportService.exportData(response, count, fileName, sheetName);
        } catch (IOException e) {
            logger.error("Excel 导出失败", e);
            handleExportError(response, e);
        } catch (Exception e) {
            logger.error("Excel 导出异常", e);
            handleExportError(response, e);
        }
    }

    /**
     * 导出大数据量（测试接口）
     * 
     * 接口地址：GET /api/excel/export/large
     * 
     * 说明：
     * - 固定导出 150000 条数据
     * - 用于测试数据超过 10万条 的截断逻辑
     * - 会在响应头中返回警告信息
     * 
     * 示例：
     * http://localhost:8080/api/excel/export/large
     * 
     * @param response HTTP 响应对象
     */
    @GetMapping("/export/large")
    public void exportLarge(HttpServletResponse response) {
        logger.info("收到大数据量导出请求");

        try {
            excelExportService.exportLargeData(response);
        } catch (IOException e) {
            logger.error("大数据量导出失败", e);
            handleExportError(response, e);
        } catch (Exception e) {
            logger.error("大数据量导出异常", e);
            handleExportError(response, e);
        }
    }

    /**
     * 处理导出错误
     */
    private void handleExportError(HttpServletResponse response, Exception e) {
        try {
            response.reset();
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            
            String errorMessage = String.format("{\"success\":false,\"message\":\"导出失败: %s\"}", 
                                               e.getMessage());
            response.getWriter().write(errorMessage);
            response.getWriter().flush();
        } catch (IOException ex) {
            logger.error("写入错误响应失败", ex);
        }
    }

    /**
     * 健康检查接口
     * 
     * 接口地址：GET /api/excel/health
     * 
     * @return 健康状态
     */
    @GetMapping("/health")
    public String health() {
        return "Excel Export Service is running!";
    }
}
