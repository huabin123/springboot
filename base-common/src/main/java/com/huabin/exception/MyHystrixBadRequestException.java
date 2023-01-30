package com.huabin.exception;

import com.netflix.hystrix.exception.HystrixBadRequestException;

/**
 * @Author huabin
 * @DateTime 2023-01-17 16:27
 * @Desc
 */
public class MyHystrixBadRequestException extends HystrixBadRequestException {

    String errorCode;

    String errorMessage;

    Throwable cause;

    @Override
    public synchronized Throwable getCause() {
        return this.cause;
    }

    public MyHystrixBadRequestException(String message, Throwable cause, String errorCode){
        super(message);
        this.errorMessage = message;
        this.errorCode = errorCode;
        this.cause = cause;
    }

    @Override
    public String toString() {
        return "MyHystrixBadRequestException{" +
                "errorCode='" + errorCode + '\'' +
                ", errorMessage='" + errorMessage + '\'' +
                ", cause=" + cause +
                '}';
    }
}
