package com.huabin.mybatisplus.transaction.entity;

import 你自己的父类实体,没有就不用设置!;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * <p>
 * 
 * </p>
 *
 * @author huabin
 * @since 2023-06-02
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ComprehensiveInfo extends 你自己的父类实体,没有就不用设置! {

    private static final long serialVersionUID = 1L;

    private String prodCode;

    private String prodName;

    private LocalDateTime dataDate;

    private String prodCls;


}
