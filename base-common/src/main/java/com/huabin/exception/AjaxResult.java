package com.huabin.exception;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * @Author huabin
 * @DateTime 2023-01-13 11:30
 * @Desc
 */

@Slf4j
@Data
public class AjaxResult<T> {

    private static final String SUCCESS_CODE = "0000";

    private static final String SUCCESS_MESSAGE = "操作成功";

    private static final String ERROR_CODE = "9999";

    private static final String ERROR_MESSAGE = "操作失败";

    private String code = "0000";

    private String message;

    private T data;

    public AjaxResult(){

    }

    public AjaxResult(String code, String message) {
        this.code = code;
        this.message = message;
        this.data = null;
    }

    public static AjaxResult error(){
        AjaxResult ajaxResult = new AjaxResult();
        ajaxResult.setCode(ERROR_CODE);
        ajaxResult.setMessage(ERROR_MESSAGE);
        ajaxResult.setData(null);
        return ajaxResult;
    }

    public static AjaxResult error(String code, String message){
        AjaxResult ajaxResult = new AjaxResult();
        ajaxResult.setCode(code);
        ajaxResult.setMessage(message);
        ajaxResult.setData(null);
        return ajaxResult;
    }

    public static <T> AjaxResult<T> ok(T data) {
        AjaxResult ajaxResult = new AjaxResult();
        ajaxResult.setCode(SUCCESS_CODE);
        ajaxResult.setMessage(SUCCESS_MESSAGE);
        ajaxResult.setData(data);
        return ajaxResult;
    }
}
