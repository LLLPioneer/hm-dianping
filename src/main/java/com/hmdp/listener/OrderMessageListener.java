package com.hmdp.listener;

import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IVoucherOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import static com.hmdp.config.RabbitMQConfig.*;

@Slf4j
@Component
public class OrderMessageListener {

    private final IVoucherOrderService voucherOrderService;

    public OrderMessageListener(IVoucherOrderService voucherOrderService) {
        this.voucherOrderService = voucherOrderService;
    }

    @RabbitListener(queues = ORDER_QUEUE)
    public void listenOrder(VoucherOrder order) {
        try {
            voucherOrderService.createVoucherOrder(order);
            log.info("订单处理成功，订单ID: {}", order.getId());
        } catch (Exception e) {
            log.error("订单处理失败，订单ID: {}", order.getId(), e);
            throw e; // 触发重试机制
        }
    }
}