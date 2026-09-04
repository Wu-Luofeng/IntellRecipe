package com.springboot.intellrecipe.item.dto;

import com.springboot.intellrecipe.item.entity.TradeOrder;
import com.springboot.intellrecipe.item.entity.TradeOrderItem;
import com.springboot.intellrecipe.item.entity.TradeOrderStatusLog;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 订单详情（主单 + 商品明细 + 状态流转日志 = 完整可追溯）
 */
@Data
public class OrderDetailDTO implements Serializable {

    private TradeOrder order;

    private List<TradeOrderItem> items;

    private List<TradeOrderStatusLog> statusLogs;
}
