package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    Result secKillVoucherOrder(Long voucherId) throws InterruptedException;

    Result createVoucherOrder(Long voucherId);

    public void createVoucherOrder(VoucherOrder voucherOrder);

    Result seckillVoucherOrder2(Long voucherId);
}
