package com.huabin.excel.export.util;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.write.builder.ExcelWriterBuilder;
import com.alibaba.excel.write.builder.ExcelWriterSheetBuilder;
import com.huabin.excel.export.constant.ExcelConstants;
import com.huabin.excel.export.model.HeaderMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * @Author huabin
 * @DateTime 2025-12-30
 * @Desc Excel 导出工具类
 * 
 * 职责：
 * 1. 封装 EasyExcel 导出逻辑
 * 2. 处理响应头设置
 * 3. 处理数据超长截断
 * 4. 支持表头映射（英文字段 -> 中文表头）
 * 
 * 设计说明：
 * - 使用 EasyExcel 进行导出，性能优异
 * - 支持大数据量导出（10万+）
 * - 自动处理文件名编码问题
 * - 支持自定义文件名和 Sheet 名称
 * - 支持英文字段名到中文表头的映射
 */
public class ExcelExportUtil {

    private static final Logger logger = LoggerFactory.getLogger(ExcelExportUtil.class);

    /**
     * 导出 Excel 到 HttpServletResponse（支持表头映射）
     * 
     * @param response       HTTP 响应对象
     * @param dataList       数据列表（字段名为英文）
     * @param headerMappings 表头映射列表（英文 -> 中文）
     * @param fileName       文件名（不含扩展名）
     * @param sheetName      Sheet 名称
     * @throws IOException IO 异常
     */
    public static void exportToResponse(HttpServletResponse response,
                                       List<LinkedHashMap<String, Object>> dataList,
                                       List<HeaderMapping> headerMappings,
                                       String fileName,
                                       String sheetName) throws IOException {
        
        if (dataList == null || dataList.isEmpty()) {
            logger.warn("导出数据为空");
            throw new IllegalArgumentException("导出数据不能为空");
        }

        // 记录原始数据总数
        int totalCount = dataList.size();
        logger.info("开始导出 Excel，原始数据量: {}", totalCount);

        // 检查数据是否超长
        boolean isOverflow = totalCount > ExcelConstants.MAX_EXPORT_ROWS;
        int exportCount = isOverflow ? ExcelConstants.MAX_EXPORT_ROWS : totalCount;

        // 如果数据超长，截断数据
        List<LinkedHashMap<String, Object>> exportData = dataList;
        if (isOverflow) {
            exportData = dataList.subList(0, ExcelConstants.MAX_EXPORT_ROWS);
            logger.warn("数据量超过最大限制 {}，实际数据量: {}，将只导出前 {} 条", 
                       ExcelConstants.MAX_EXPORT_ROWS, totalCount, exportCount);
        }

        // 设置响应头
        setResponseHeaders(response, fileName, totalCount, exportCount, isOverflow);

        // 获取输出流
        OutputStream outputStream = null;
        try {
            outputStream = response.getOutputStream();

            // 提取中文表头（用于 Excel 显示）
            List<String> chineseHeaders = extractChineseHeaders(headerMappings);
            logger.info("Excel 表头（中文）: {}", chineseHeaders);

            // 提取英文字段名（用于从数据中取值）
            List<String> englishFields = extractEnglishFields(headerMappings);
            logger.info("数据字段（英文）: {}", englishFields);

            // 转换数据为 List<List<Object>> 格式（按照映射顺序）
            List<List<Object>> excelData = convertToExcelDataWithMapping(exportData, englishFields);

            // 使用 EasyExcel 导出
            long startTime = System.currentTimeMillis();
            
            ExcelWriterBuilder writerBuilder = EasyExcel.write(outputStream);
            ExcelWriterSheetBuilder sheetBuilder = writerBuilder.sheet(sheetName);
            
            // 设置表头（中文）
            sheetBuilder.head(convertHeadersToList(chineseHeaders));
            
            // 写入数据
            sheetBuilder.doWrite(excelData);

            long endTime = System.currentTimeMillis();
            logger.info("Excel 导出成功，导出数据量: {}, 耗时: {}ms", exportCount, (endTime - startTime));

        } catch (IOException e) {
            logger.error("Excel 导出失败", e);
            throw e;
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.flush();
                    outputStream.close();
                } catch (IOException e) {
                    logger.error("关闭输出流失败", e);
                }
            }
        }
    }

    /**
     * 设置响应头
     */
    private static void setResponseHeaders(HttpServletResponse response,
                                          String fileName,
                                          int totalCount,
                                          int exportCount,
                                          boolean isOverflow) throws UnsupportedEncodingException {
        
        // 设置 Content-Type
        response.setContentType(ExcelConstants.CONTENT_TYPE);
        response.setCharacterEncoding(ExcelConstants.CHARSET_UTF8);

        // 设置文件名（URL 编码，解决中文乱码）
        String encodedFileName = URLEncoder.encode(fileName, ExcelConstants.CHARSET_UTF8)
                .replaceAll("\\+", "%20");
        String fullFileName = encodedFileName + ExcelConstants.EXCEL_EXTENSION;
        
        response.setHeader(ExcelConstants.CONTENT_DISPOSITION, 
                          "attachment;filename=" + fullFileName + ";filename*=utf-8''" + fullFileName);

        // 设置自定义响应头
        response.setHeader(ExcelConstants.HEADER_TOTAL_COUNT, String.valueOf(totalCount));
        response.setHeader(ExcelConstants.HEADER_EXPORT_COUNT, String.valueOf(exportCount));

        // 如果数据超长，设置警告信息
        if (isOverflow) {
            String warningMessage = String.format("数据总量 %d 条，超过最大导出限制 %d 条，已截断为前 %d 条",
                                                 totalCount, ExcelConstants.MAX_EXPORT_ROWS, exportCount);
            response.setHeader(ExcelConstants.HEADER_DATA_OVERFLOW, 
                              URLEncoder.encode(warningMessage, ExcelConstants.CHARSET_UTF8));
            
            logger.warn("设置数据超长警告响应头: {}", warningMessage);
        }
    }

    /**
     * 提取中文表头（用于 Excel 显示）
     */
    private static List<String> extractChineseHeaders(List<HeaderMapping> headerMappings) {
        if (headerMappings == null || headerMappings.isEmpty()) {
            throw new IllegalArgumentException("表头映射列表不能为空");
        }
        
        List<String> chineseHeaders = new java.util.ArrayList<>();
        for (HeaderMapping mapping : headerMappings) {
            chineseHeaders.add(mapping.getChineseName());
        }
        return chineseHeaders;
    }

    /**
     * 提取英文字段名（用于从数据中取值）
     */
    private static List<String> extractEnglishFields(List<HeaderMapping> headerMappings) {
        if (headerMappings == null || headerMappings.isEmpty()) {
            throw new IllegalArgumentException("表头映射列表不能为空");
        }
        
        List<String> englishFields = new java.util.ArrayList<>();
        for (HeaderMapping mapping : headerMappings) {
            englishFields.add(mapping.getEnglishName());
        }
        return englishFields;
    }

    /**
     * 将表头转换为 EasyExcel 需要的格式
     */
    private static List<List<String>> convertHeadersToList(List<String> headers) {
        List<List<String>> headerList = new java.util.ArrayList<>();
        for (String header : headers) {
            List<String> head = new java.util.ArrayList<>();
            head.add(header);
            headerList.add(head);
        }
        return headerList;
    }

    /**
     * 将 LinkedHashMap 数据转换为 List<List<Object>> 格式（按照映射顺序）
     * 
     * @param dataList      数据列表（字段名为英文）
     * @param englishFields 英文字段名列表（决定取值顺序）
     * @return Excel 数据
     */
    private static List<List<Object>> convertToExcelDataWithMapping(
            List<LinkedHashMap<String, Object>> dataList,
            List<String> englishFields) {
        
        List<List<Object>> excelData = new java.util.ArrayList<>();
        
        for (LinkedHashMap<String, Object> row : dataList) {
            List<Object> rowData = new java.util.ArrayList<>();
            
            // 按照英文字段名顺序提取值
            for (String englishField : englishFields) {
                Object value = row.get(englishField);
                rowData.add(value != null ? value : "");
            }
            
            excelData.add(rowData);
        }
        
        return excelData;
    }

    /**
     * 生成默认文件名
     * 格式：export_data_yyyyMMdd_HHmmss
     */
    public static String generateDefaultFileName() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
        return ExcelConstants.DEFAULT_FILE_PREFIX + sdf.format(new Date());
    }
}
