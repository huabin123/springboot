package com.huabin.exception.constant;

import cn.hutool.core.util.StrUtil;

import java.text.MessageFormat;

public interface ErrorCode {

    String getCode();

    String getMessage();

    default String getMessage(Object... strings){
        return MessageFormat.format(this.getMessage(), strings);
    }

    default ErrorCode format(Object... strings){
        ErrorCodeImpl errorCodeImpl = new ErrorCodeImpl();
        errorCodeImpl.setCode(this.getCode());
        errorCodeImpl.setMessage(this.getMessage(strings));
        return errorCodeImpl;
    }

    default boolean isSuccess(){
        return StrUtil.equals("0000", this.getCode());
    }

    public static class ErrorCodeImpl implements ErrorCode {
        public void setCode(String code) {
            this.code = code;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        private String code;
        private String message;

        @Override
        public String getCode() {
            return code;
        }

        @Override
        public String getMessage() {
            return message;
        }


    }

}
