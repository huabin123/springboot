package com.huabin.mybatis.entity;

import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
* Created by Mybatis Generator2023/04/26
* Database table is comprehensive_info()
*/
@Data
public class ComprehensiveInfo implements Serializable {
    private String prodCode;

    private String prodName;

    private Date dataDate;

    private String prodCls;

    private static final long serialVersionUID = 1L;
}