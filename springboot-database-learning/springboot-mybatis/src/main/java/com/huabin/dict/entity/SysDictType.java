package com.huabin.dict.entity;

import java.io.Serializable;

/**
 * 字典类型实体类
 */
public class SysDictType implements Serializable {
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
     * 父字典ID
     */
    private Long dictPid;

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

    public Long getDictPid() {
        return dictPid;
    }

    public void setDictPid(Long dictPid) {
        this.dictPid = dictPid;
    }

    @Override
    public String toString() {
        return "SysDictType{" +
                "dictId=" + dictId +
                ", dictName='" + dictName + '\'' +
                ", dictPid=" + dictPid +
                '}';
    }
}
