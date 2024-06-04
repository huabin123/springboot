package com.huabin.sharding.order.entity;

import java.time.LocalDateTime;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * <p>
 * 订单表
 * </p>
 *
 * @author huabin
 * @since 2024-05-30
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class OrderInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    private String id;

    /**
     * 订单号
     */
    private String orderNo;

    /**
     * 客户手机号
     */
    private String mobile;

    /**
     * 状态(1-已下单 2-已支付 3-退款 4-订单取消)
     */
    private Integer status;

    /**
     * 下单时间
     */
    private LocalDateTime orderTime;

    /**
     * 支付时间
     */
    private LocalDateTime payTime;

    /**
     * 退款时间
     */
    private LocalDateTime refundTime;

    /**
     * 订单自动取消时间
     */
    private LocalDateTime cancelOrderTime;

    /**
     * 创建时间
     */
    private LocalDateTime createDate;

    /**
     * 更新时间
     */
    private LocalDateTime updateDate;

    /**
     * 创建人
     */
    private String createUser;

    /**
     * 更新人
     */
    private String updateUser;

    /**
     * 删除标识
     */
    private Boolean deleteFlag;


}
