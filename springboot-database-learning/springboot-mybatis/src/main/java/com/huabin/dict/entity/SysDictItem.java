package com.huabin.dict.entity;

import java.io.Serializable;

/**
 * 字典项实体类
 */
public class SysDictItem implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 字典项ID
     */
    private Long itemId;

    /**
     * 字典ID
     */
    private Long dictId;

    /**
     * 字典项名称
     */
    private String itemName;

    /**
     * 字典项值
     */
    private String itemValue;

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public Long getDictId() {
        return dictId;
    }

    public void setDictId(Long dictId) {
        this.dictId = dictId;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public String getItemValue() {
        return itemValue;
    }

    public void setItemValue(String itemValue) {
        this.itemValue = itemValue;
    }

    @Override
    public String toString() {
        return "SysDictItem{" +
                "itemId=" + itemId +
                ", dictId=" + dictId +
                ", itemName='" + itemName + '\'' +
                ", itemValue='" + itemValue + '\'' +
                '}';
    }
}
