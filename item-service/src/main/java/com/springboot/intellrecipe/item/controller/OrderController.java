package com.springboot.intellrecipe.item.controller;

import com.springboot.intellrecipe.common.dto.Result;
import com.springboot.intellrecipe.common.dto.UserDTO;
import com.springboot.intellrecipe.common.utils.UserHolder;
import com.springboot.intellrecipe.item.dto.CreateOrderDTO;
import com.springboot.intellrecipe.item.service.OrderService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * 商城订单接口（需登录，登录拦截见 item-service WebMvcConfig）
 */
@RestController
@RequestMapping("/order")
public class OrderController {

    @Resource
    private OrderService orderService;

    /**
     * 购物车结算下单（自动按商家拆单）
     *
     * @return 生成的订单号列表
     */
    @PostMapping("/create")
    public Result<?> create(@RequestBody CreateOrderDTO dto) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        try {
            return Result.ok(orderService.createOrders(user.getId(), dto));
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 我的订单列表（status 可选过滤）
     */
    @GetMapping("/list")
    public Result<?> list(@RequestParam(value = "status", required = false) Integer status) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        return Result.ok(orderService.queryMyOrders(user.getId(), status));
    }

    /**
     * 订单详情（主单 + 明细 + 状态时间线，可追溯）
     */
    @GetMapping("/detail")
    public Result<?> detail(@RequestParam("orderNo") String orderNo) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        try {
            return Result.ok(orderService.queryOrderDetail(user.getId(), orderNo));
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 支付（当前支付即完成）
     */
    @PostMapping("/pay")
    public Result<Void> pay(@RequestParam("orderNo") String orderNo) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        try {
            orderService.pay(user.getId(), orderNo);
            return Result.ok();
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 取消订单（待支付可取消，用券自动退券）
     */
    @PostMapping("/cancel")
    public Result<Void> cancel(@RequestParam("orderNo") String orderNo) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        try {
            orderService.cancel(user.getId(), orderNo);
            return Result.ok();
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 查询订单当前状态（结算提交后前端轮询：4处理中 / 0待支付 / 5下单失败 / …）
     */
    @GetMapping("/status")
    public Result<?> status(@RequestParam("orderNo") String orderNo) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        try {
            return Result.ok(orderService.queryOrderStatus(user.getId(), orderNo));
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage());
        }
    }
}
