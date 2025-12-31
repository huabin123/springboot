package com.huabin.multids.enums;

/**
 * @Author huabin
 * @DateTime 2025-12-29
 * @Desc 产品创建状态枚举
 */
public enum ProductCreateStatus {
    
    /**
     * 创建中
     */
    CREATING(0, "创建中"),
    
    /**
     * 创建成功
     */
    SUCCESS(1, "创建成功"),
    
    /**
     * 创建失败
     */
    FAILED(2, "创建失败");

    private final Integer code;
    private final String desc;

    ProductCreateStatus(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public Integer getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    /**
     * 根据code获取枚举
     */
    public static ProductCreateStatus getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (ProductCreateStatus status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        return null;
    }
}
