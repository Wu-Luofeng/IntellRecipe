package com.springboot.intellrecipe.api.client;

import com.springboot.intellrecipe.common.dto.Result;
import com.springboot.intellrecipe.api.dto.VoucherBriefDTO;
import com.springboot.intellrecipe.api.dto.VoucherPrecheckDTO;

import com.springboot.intellrecipe.api.dto.VoucherReleaseDTO;
import com.springboot.intellrecipe.api.dto.VoucherUseDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;

/**
 * 服务间契约：item-service → voucher-service 的优惠券核销/退券。
 * 由 intellrecipe-api 模块承载，避免调用方依赖被调方业务实体。
 */
@FeignClient(name = "voucher-service", path = "/internal/voucher")
public interface VoucherClient {

    /**
     * 商城结算核销一张用户券（幂等：同 orderNo 重复调用返回原抵扣额）
     *
     * @param useDTO 核销请求
     * @return Result.data = 抵扣金额（元）
     */
    @PostMapping("/use")
    Result<BigDecimal> useVoucher(@RequestBody VoucherUseDTO useDTO);

    /**
     * 结算前校验并返回一张券的信息（仅校验归属/状态/有效期/是否已核销，不做状态变更）
     *
     * @param precheck 请求
     * @return Result.data = 券简况（含 shopId / 门槛 / 有效期）
     */
    @PostMapping("/precheck")
    Result<VoucherBriefDTO> precheck(@RequestBody VoucherPrecheckDTO precheck);

    /**
     * 商城订单取消时退回一张券（仅允许退回核销到指定订单的券）
     *
     * @param releaseDTO 退券请求
     */
    @PostMapping("/release")
    Result<Void> releaseVoucher(@RequestBody VoucherReleaseDTO releaseDTO);
}
