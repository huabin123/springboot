package com.huabin.excel.export;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @Author huabin
 * @DateTime 2025-12-30
 * @Desc Excel 导出应用启动类
 */
@SpringBootApplication
public class ExcelExportApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExcelExportApplication.class, args);
        System.out.println("========================================");
        System.out.println("Excel 导出服务启动成功！");
        System.out.println("访问地址: http://localhost:8080");
        System.out.println("导出接口: http://localhost:8080/api/excel/export");
        System.out.println("========================================");
    }
}
