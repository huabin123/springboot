package com.huabin.exception;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.Map;

/**
 * @Author huabin
 * @DateTime 2023-01-13 13:08
 * @Desc
 */

@ControllerAdvice(basePackages = {"com.huabin"})
public class CustomResponseBodyAdvice implements ResponseBodyAdvice {
    public CustomResponseBodyAdvice() {
    }

    @Override
    public boolean supports(MethodParameter returnType, Class converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object returnValue, MethodParameter returnType, MediaType selectedContentType,
                                  Class selectedConverterType, ServerHttpRequest request, ServerHttpResponse response) {
        if(returnValue instanceof AjaxResult ||
                (returnValue instanceof Map && ((Map) returnValue).get("ignoreAjaxResult") != null)){
            return returnValue;
        } else if (returnValue instanceof Throwable) {
            return AjaxResult.error();
        } else {
            return AjaxResult.ok(returnValue);
        }
    }
}
