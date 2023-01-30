package com.huabin.exception.constant;


public enum CommonCode implements ErrorCode {
    COMMON_SUCCESS("0000", "操作成功！"),
    COMMON_ERROR("9999", "操作失败！");

    private String code;
    private String message;

    private CommonCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    @Override
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
