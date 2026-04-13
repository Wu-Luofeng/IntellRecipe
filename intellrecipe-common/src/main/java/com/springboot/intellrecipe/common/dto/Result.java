package com.springboot.intellrecipe.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> implements Serializable {
    private Boolean success;
    private String errorMsg;
    private T data;
    private Long total;

    public static <T> Result<T> ok() {
        return new Result<>(true, null, null, null);
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(true, null, data, null);
    }

    public static <T> Result<T> ok(List<T> data, Long total) {
        return new Result<>(true, null, (T) data, total);
    }

    public static <T> Result<T> fail(String errorMsg) {
        return new Result<>(false, errorMsg, null, null);
    }
}
