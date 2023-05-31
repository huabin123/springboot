package com.huabin.transactional.entity;

import java.io.Serializable;
import lombok.Data;

/**
* Created by Mybatis Generator2023/05/24
* Database table is prices()
*/
@Data
public class Prices implements Serializable {
    private Integer pid;

    private String category;

    private Float price;

    private static final long serialVersionUID = 1L;
}