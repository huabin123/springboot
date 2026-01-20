package com.huabin.multids.dto;

import java.io.Serializable;
import java.util.List;

/**
 * @Author huabin
 * @DateTime 2025-12-29
 * @Desc 批量创建产品请求DTO
 */
public class BatchCreateRequest implements Serializable {
    
    private static final long serialVersionUID = 1L;

    /**
     * 创建人
     */
    private String creator;

    /**
     * 产品列表
     */
    private List<ProductCreateRequest> products;

    // Getter and Setter

    public String getCreator() {
        return creator;
    }

    public void setCreator(String creator) {
        this.creator = creator;
    }

    public List<ProductCreateRequest> getProducts() {
        return products;
    }

    public void setProducts(List<ProductCreateRequest> products) {
        this.products = products;
    }

    @Override
    public String toString() {
        return "BatchCreateRequest{" +
                "creator='" + creator + '\'' +
                ", products=" + products +
                '}';
    }
}
