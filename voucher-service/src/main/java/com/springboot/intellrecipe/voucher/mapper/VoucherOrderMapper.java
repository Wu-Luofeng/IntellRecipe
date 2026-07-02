package com.springboot.intellrecipe.voucher.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.springboot.intellrecipe.common.entity.VoucherOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.springboot.intellrecipe.common.dto.MyVoucherDTO;
import java.util.List;

@Mapper
public interface VoucherOrderMapper extends BaseMapper<VoucherOrder> {

    @Select("SELECT vo.id AS orderId, v.id AS voucherId, v.title, v.sub_title AS subTitle, "
       + "v.pay_value AS payValue, v.actual_value AS actualValue, v.type, "
       + "m.name AS shopName, m.id AS shopId, vo.status, vo.create_time AS createTime, vo.expire_time AS expireTime "
       + "FROM voucher_order vo "
       + "LEFT JOIN voucher v ON vo.voucher_id = v.id "
       + "LEFT JOIN merchant m ON v.shop_id = m.id "
       + "WHERE vo.user_id = #{userId} ORDER BY vo.create_time DESC")
    List<MyVoucherDTO> selectMyVouchers(Long userId);

    @Update("UPDATE voucher_order SET status = 0, update_time = NOW() "
       + "WHERE status = 1 AND expire_time IS NOT NULL AND expire_time < NOW()")
    int updateExpiredVouchers();
}