package com.huabin.exception;

import com.huabin.exception.constant.CommonCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;


@Slf4j
@ControllerAdvice
@ResponseBody
public class GlobalExceptionHandler {
    @ExceptionHandler({BusinessException.class})
    public AjaxResult handleBusinessException(BusinessException e) {
        log.error("ErrorCode: {}, ErrorMessage: {}", e.getErrorCode(), e.getErrorMessage(), e);
        return AjaxResult.error(e.getErrorCode(), e.getErrorMessage());
    }

    @ExceptionHandler({Exception.class})
    public AjaxResult handleException(Exception e) {
        log.error("系统异常", e);
        return AjaxResult.error(CommonCode.COMMON_ERROR.getCode(), "系统异常");
    }
}
