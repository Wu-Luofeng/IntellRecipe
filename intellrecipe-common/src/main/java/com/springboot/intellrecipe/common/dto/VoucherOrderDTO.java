package com.springboot.intellrecipe.common.dto;

import lombok.Data;
import java.io.Serializable;

@Data
public class VoucherOrderDTO implements Serializable {
    private Long userId;
    private Long voucherId;
    private Long orderId;
    private Integer type; // 0:普通券, 1:秒杀券
}
