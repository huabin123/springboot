package com.huabin.mybatisplus.transaction.entity;

import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * <p>
 * 
 * </p>
 *
 * @author huabin
 * @since 2023-06-12
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class SingleTable2 implements Serializable {

    private static final long serialVersionUID = 1L;

    private String key1;

    private Integer key2;

    private String key3;

    private String keyPart1;

    private String keyPart2;

    private String keyPart3;

    private String commonField;


}
