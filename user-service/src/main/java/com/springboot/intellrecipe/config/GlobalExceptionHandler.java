package com.springboot.intellrecipe.config;

import com.springboot.intellrecipe.common.dto.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(RuntimeException.class)
    public Result handleRuntimeException(RuntimeException e) {
        String message = e.getMessage();
        // 针对业务预期的异常，只打印简单日志，不打印堆栈
        if ("不能重复下单".equals(message) || "库存不足".equals(message) || "秒杀尚未开始！".equals(message) || "秒杀已经结束".equals(message)) {
            log.warn("业务异常拦截: {}", message);
        } else {
            // 其他未知异常，打印完整堆栈
            log.error("系统异常", e);
        }
        return Result.fail(message);
    }
}
