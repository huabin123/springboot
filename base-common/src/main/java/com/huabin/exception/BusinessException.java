package com.huabin.exception;

import com.huabin.exception.constant.CommonCode;
import com.huabin.exception.constant.ErrorCode;

/**
 * 业务异常
 * @author wms5757
 */
public class BusinessException extends RuntimeException {
    String errorCode;
    String errorMessage;

    public BusinessException(String errorCode) {
        this.errorCode = errorCode;
        this.errorMessage = CommonCode.valueOf(errorCode).getMessage();
    }
    public BusinessException(String errorCode, String errorMessage){
        super(errorMessage);
        this.errorCode=errorCode;
        this.errorMessage=errorMessage;
    }
    public BusinessException(ErrorCode code, Object... params){
        super(code.getMessage(params));
        this.errorCode=code.getCode();
        this.errorMessage=code.getMessage(params);
    }

    public BusinessException(String errorCode,String errorMessage, Throwable throwable){
        super(errorMessage,throwable);
        this.errorCode=errorCode;
        this.errorMessage=errorMessage;
    }
    public BusinessException(ErrorCode code, Throwable throwable){
        super(code.getMessage(),throwable);
        this.errorCode=code.getCode();
        this.errorMessage=code.getMessage();
    }
    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public String toString() {
        return "BusinessException{" +
                "errorCode='" + errorCode + '\'' +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }
}
