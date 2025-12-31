package com.huabin.excel.export.service;

import com.huabin.excel.export.constant.ExcelConstants;
import com.huabin.excel.export.model.HeaderMapping;
import com.huabin.excel.export.util.ExcelExportUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * @Author huabin
 * @DateTime 2025-12-30
 * @Desc Excel 导出服务
 * 
 * 职责：
 * 1. 协调数据查询和导出
 * 2. 业务逻辑处理
 * 3. 异常处理
 * 
 * 设计说明：
 * - Service 层负责业务逻辑编排
 * - 调用 MockDataService 获取数据
 * - 调用 ExcelExportUtil 执行导出
 * - 统一异常处理
 */
@Service
public class ExcelExportService {

    private static final Logger logger = LoggerFactory.getLogger(ExcelExportService.class);

    @Autowired
    private MockDataService mockDataService;

    /**
     * 导出数据到 Excel
     * 
     * @param response  HTTP 响应对象
     * @param count     数据条数
     * @param fileName  文件名（可选）
     * @param sheetName Sheet 名称（可选）
     * @throws IOException IO 异常
     */
    public void exportData(HttpServletResponse response, 
                          Integer count, 
                          String fileName, 
                          String sheetName) throws IOException {
        
        logger.info("========================================");
        logger.info("开始执行 Excel 导出");
        logger.info("请求数据量: {}", count);
        logger.info("文件名: {}", fileName);
        logger.info("Sheet名称: {}", sheetName);
        logger.info("========================================");

        try {
            // 1. 参数校验和默认值设置
            count = validateAndSetDefaultCount(count);
            fileName = validateAndSetDefaultFileName(fileName);
            sheetName = validateAndSetDefaultSheetName(sheetName);

            // 2. 查询数据（模拟从数据库查询，返回英文字段名）
            List<LinkedHashMap<String, Object>> dataList = mockDataService.queryData(count);

            // 3. 获取表头映射配置（英文 -> 中文）
            List<HeaderMapping> headerMappings = mockDataService.getHeaderMappings();

            // 4. 执行导出（传入表头映射）
            ExcelExportUtil.exportToResponse(response, dataList, headerMappings, fileName, sheetName);

            logger.info("Excel 导出完成");
            logger.info("========================================");

        } catch (Exception e) {
            logger.error("Excel 导出失败", e);
            throw e;
        }
    }

    /**
     * 校验并设置默认数据条数
     */
    private Integer validateAndSetDefaultCount(Integer count) {
        if (count == null || count <= 0) {
            logger.warn("数据条数无效: {}, 使用默认值: 1000", count);
            return 1000;
        }
        
        // 限制最大查询数量（防止内存溢出）
        int maxQueryCount = 1000000; // 最大查询100万条
        if (count > maxQueryCount) {
            logger.warn("数据条数超过最大限制: {}, 限制为: {}", count, maxQueryCount);
            return maxQueryCount;
        }
        
        return count;
    }

    /**
     * 校验并设置默认文件名
     */
    private String validateAndSetDefaultFileName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            fileName = ExcelExportUtil.generateDefaultFileName();
            logger.info("使用默认文件名: {}", fileName);
        }
        return fileName;
    }

    /**
     * 校验并设置默认 Sheet 名称
     */
    private String validateAndSetDefaultSheetName(String sheetName) {
        if (sheetName == null || sheetName.trim().isEmpty()) {
            sheetName = ExcelConstants.DEFAULT_SHEET_NAME;
            logger.info("使用默认 Sheet 名称: {}", sheetName);
        }
        return sheetName;
    }

    /**
     * 导出大数据量（用于测试超过10万条的场景）
     * 
     * @param response HTTP 响应对象
     * @throws IOException IO 异常
     */
    public void exportLargeData(HttpServletResponse response) throws IOException {
        logger.info("开始导出大数据量（测试超过10万条场景）");
        
        // 生成 150000 条数据，测试截断逻辑
        int largeCount = 150000;
        exportData(response, largeCount, "大数据量导出测试", "订单数据");
    }
}
