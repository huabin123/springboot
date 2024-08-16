package com.huabin.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.*;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 一张票据下有多项费用科目
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
@Accessors(chain = true)
public class BillExpenseDetail {
    @ExcelProperty("票据编号")
    private String number;
    @ExcelProperty("创建时间")
    private Date createDate;
    @ExcelProperty("收支方向")
    private String direction;
    @ExcelProperty("总金额")
    private BigDecimal totalAmount;

    @ExcelProperty("名称规格")
    private String subject;
    @ExcelProperty("单价")
    private String price;
    @ExcelProperty("数量")
    private String quantity;
    @ExcelProperty("单位")
    private String unit;
    @ExcelProperty("金额")
    private BigDecimal amount;
    @ExcelProperty("名称规格1")
    private String subject0 = "名称规格";
    @ExcelProperty("名称规格2")
    private String subject1 = "名称规格";
    @ExcelProperty("名称规格3")
    private String subject2 = "名称规格";
    @ExcelProperty("名称规格4")
    private String subject3 = "名称规格";
    @ExcelProperty("名称规格5")
    private String subject4 = "名称规格";
    @ExcelProperty("名称规格6")
    private String subject5 = "名称规格";
    @ExcelProperty("名称规格7")
    private String subject6 = "名称规格";
    @ExcelProperty("名称规格8")
    private String subject7 = "名称规格";
    @ExcelProperty("名称规格9")
    private String subject8 = "名称规格";
    @ExcelProperty("名称规格10")
    private String subject9 = "名称规格";

}
