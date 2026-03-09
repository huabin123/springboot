package com.huabin.dict.vo;

import com.huabin.dict.entity.SysDictItem;

import java.io.Serializable;
import java.util.List;

/**
 * 字典类型VO - 包含字典项列表
 */
public class SysDictTypeVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 字典ID
     */
    private Long dictId;

    /**
     * 字典名称
     */
    private String dictName;

    /**
     * 字典项列表
     */
    private List<SysDictItem> items;

    public Long getDictId() {
        return dictId;
    }

    public void setDictId(Long dictId) {
        this.dictId = dictId;
    }

    public String getDictName() {
        return dictName;
    }

    public void setDictName(String dictName) {
        this.dictName = dictName;
    }

    public List<SysDictItem> getItems() {
        return items;
    }

    public void setItems(List<SysDictItem> items) {
        this.items = items;
    }

    @Override
    public String toString() {
        return "SysDictTypeVO{" +
                "dictId=" + dictId +
                ", dictName='" + dictName + '\'' +
                ", items=" + items +
                '}';
    }
}
