package com.springboot.intellrecipe.item.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 购物车结算下单请求
 */
@Data
public class CreateOrderDTO implements Serializable {

    /** 参与结算的购物车条目ID；为空则默认结算当前用户所有“已勾选(selected=1)”条目 */
    private List<Long> cartIds;

    /** 可选：使用的优惠券实例ID（voucher_order.id），券必须属于本单商家且满足门槛 */
    private Long voucherOrderId;

    /** 收货人 */
    private String receiverName;

    /** 收货电话 */
    private String receiverPhone;

    /** 收货地址 */
    private String receiverAddress;

    /** 买家备注 */
    private String remark;

    /** 结算幂等键：一次结算会话生成一次（前端 uuid），重复提交后端据此返回原订单 */
    private String clientToken;
}
