package com.huabin.excel.export.constant;

/**
 * @Author huabin
 * @DateTime 2025-12-30
 * @Desc Excel 导出常量定义
 */
public class ExcelConstants {

    /**
     * 最大导出数据条数
     * 超过此数量将截断数据并在响应头中返回警告信息
     */
    public static final int MAX_EXPORT_ROWS = 100000;

    /**
     * 响应头 - 数据超长警告
     */
    public static final String HEADER_DATA_OVERFLOW = "X-Data-Overflow";

    /**
     * 响应头 - 实际数据总数
     */
    public static final String HEADER_TOTAL_COUNT = "X-Total-Count";

    /**
     * 响应头 - 导出数据条数
     */
    public static final String HEADER_EXPORT_COUNT = "X-Export-Count";

    /**
     * 默认 Sheet 名称
     */
    public static final String DEFAULT_SHEET_NAME = "数据导出";

    /**
     * 默认文件名前缀
     */
    public static final String DEFAULT_FILE_PREFIX = "export_data_";

    /**
     * Excel 文件扩展名
     */
    public static final String EXCEL_EXTENSION = ".xlsx";

    /**
     * Content-Type
     */
    public static final String CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /**
     * Content-Disposition 头名称
     */
    public static final String CONTENT_DISPOSITION = "Content-Disposition";

    /**
     * 字符编码
     */
    public static final String CHARSET_UTF8 = "UTF-8";

    private ExcelConstants() {
        // 私有构造函数，防止实例化
    }
}
