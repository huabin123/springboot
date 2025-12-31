package com.huabin.excel.export.model;

import java.io.Serializable;

/**
 * @Author huabin
 * @DateTime 2025-12-30
 * @Desc 表头映射实体类
 * 
 * 用途：
 * 1. 定义数据库字段（英文）到 Excel 表头（中文）的映射关系
 * 2. 保证导出时字段顺序和表头显示
 * 
 * 设计说明：
 * - englishName: 数据库字段名（英文），用于从 Map 中取值
 * - chineseName: Excel 表头名称（中文），用于显示
 * - 映射列表的顺序决定了 Excel 列的顺序
 */
public class HeaderMapping implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 英文字段名（数据库字段名）
     */
    private String englishName;

    /**
     * 中文字段名（Excel 表头名称）
     */
    private String chineseName;

    public HeaderMapping() {
    }

    public HeaderMapping(String englishName, String chineseName) {
        this.englishName = englishName;
        this.chineseName = chineseName;
    }

    public String getEnglishName() {
        return englishName;
    }

    public void setEnglishName(String englishName) {
        this.englishName = englishName;
    }

    public String getChineseName() {
        return chineseName;
    }

    public void setChineseName(String chineseName) {
        this.chineseName = chineseName;
    }

    @Override
    public String toString() {
        return "HeaderMapping{" +
                "englishName='" + englishName + '\'' +
                ", chineseName='" + chineseName + '\'' +
                '}';
    }
}
