package com.springboot.intellrecipe.voucher.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.springboot.intellrecipe.voucher.entity.DeadLetter;

public interface DeadLetterService extends IService<DeadLetter> {
    
    /**
     * 补偿重试某条死信消息
     */
    boolean retryDeadLetter(Long id);
}