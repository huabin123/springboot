package com.huabin.mybatisplus.transaction.entity;

import java.time.LocalDateTime;
import java.io.Serializable;
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
@EqualsAndHashCode(callSuper = false)
public class ComprehensiveInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    private String prodCode;

    private String prodName;

    private LocalDateTime dataDate;

    private String prodCls;


}
