package com.huabin.mybatisplus.transaction.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import 你自己的父类实体,没有就不用设置!;
import com.baomidou.mybatisplus.annotation.TableId;
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
public class Prices extends 你自己的父类实体,没有就不用设置! {

    private static final long serialVersionUID = 1L;

    @TableId(value = "pid", type = IdType.AUTO)
    private Integer pid;

    private String category;

    private Float price;


}
