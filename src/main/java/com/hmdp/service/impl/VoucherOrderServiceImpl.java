
package com.hmdp.service.impl;

import com.hmdp.config.RabbitMQConfig;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.GlobalUniqueIDGenerator;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.PathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 1
 * @since  1
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    //    private SimpleRedisLock simpleRedisLock;
    //                                   SimpleRedisLock simpleRedisLock,
    //        this.simpleRedisLock = simpleRedisLock;
    private SeckillVoucherServiceImpl seckillVoucherService;
    private GlobalUniqueIDGenerator globalUniqueIDGenerator;
    private RedissonClient redissonClient;
    private StringRedisTemplate stringRedisTemplate;
    private final static DefaultRedisScript<Long> SECKILL_ORDER_SERVICE_SCRIPT;
    private RabbitTemplate rabbitTemplate;
    static {
        SECKILL_ORDER_SERVICE_SCRIPT = new DefaultRedisScript<>();
        SECKILL_ORDER_SERVICE_SCRIPT.setLocation(new ClassPathResource("lua/SeckillOrderServiceScript.lua"));
        SECKILL_ORDER_SERVICE_SCRIPT.setResultType(Long.class);
    }
    @Autowired

    public VoucherOrderServiceImpl(SeckillVoucherServiceImpl seckillVoucherService,
                                   GlobalUniqueIDGenerator globalUniqueIDGenerator,
                                   RedissonClient redissonClient,
                                   StringRedisTemplate stringRedisTemplate,
                                   RabbitTemplate rabbitTemplate){
        this.seckillVoucherService = seckillVoucherService;
        this.globalUniqueIDGenerator = globalUniqueIDGenerator;
        this.redissonClient = redissonClient;
        this.stringRedisTemplate = stringRedisTemplate;
        this.rabbitTemplate = rabbitTemplate;
    }



    /**
     * 优化后的优惠劵秒杀下单
     * @param voucherId 优惠券id
     * @return 优惠券秒杀订单信息
     */
    @Override
    public Result seckillVoucherOrder2(Long voucherId) {
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        if (seckillVoucher==null)return Result.fail("优惠券不存在");
        //判断是否开始或者是否已经结束
        if(LocalDateTime.now().isBefore(seckillVoucher.getBeginTime()) || LocalDateTime.now().isAfter(seckillVoucher.getEndTime())){
            return Result.fail("秒杀活动还未开始或已经结束");
        }
        Long result = stringRedisTemplate.execute(SECKILL_ORDER_SERVICE_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                UserHolder.getUser().getId().toString());
        long r = result.longValue();

        if (r != 0){
            return Result.fail(r==1 ? "库存不足" : "不允许重复下单");
        }

        //生成订单加入队列,获取代理对象
        Long orderId = globalUniqueIDGenerator.getGlobalUniqueID("order");
        VoucherOrder voucherOrder = VoucherOrder
                .builder()
                .id(orderId)
                .voucherId(voucherId)
                .userId(UserHolder.getUser().getId())
                .build();

        // 发送订单消息到RabbitMQ
        rabbitTemplate.convertAndSend(
            RabbitMQConfig.ORDER_EXCHANGE,
            RabbitMQConfig.ORDER_ROUTING_KEY,
            voucherOrder
        );

        return Result.ok(voucherOrder);
    }

    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder){
        Long voucherId = voucherOrder.getVoucherId();
        //查询数据库避免重复下单
        int count = query().eq("user_id",voucherOrder.getUserId()).eq("voucher_id",voucherId).count();
        if (count > 0){
            log.error("请勿重复下单");
        }
        //扣减库存(乐观锁CAS法)
        boolean updateResult = seckillVoucherService
                .update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock",0)
                .update();
        if (!updateResult){
            log.error("优惠券库存不足");
        }
        //创建订单
        this.save(voucherOrder);
    }



    @Override
    public Result secKillVoucherOrder(Long voucherId) throws InterruptedException {
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        if (seckillVoucher==null)return Result.fail("优惠券不存在");
        //判断是否开始或者是否已经结束
        if(LocalDateTime.now().isBefore(seckillVoucher.getBeginTime()) || LocalDateTime.now().isAfter(seckillVoucher.getEndTime())){
            return Result.fail("秒杀活动还未开始或已经结束");
        }
        //判断库存是否充足
        if (seckillVoucher.getStock() < 1){
            return Result.fail("优惠券库存不足");
        }

        //synchronized锁实现方案
        //intern()确保只要用户id相同toString得到的字符串就是相同的(toString底层new了新的字符串)
//        log.info("VoucherOrderServiceImpl,userid{}",userId);
//        synchronized (userId.toString().intern()){
//            //解决事务失效问题,获取代理对象
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            //实现业务并返回订单
//            return proxy.createVoucherOrder(voucherId);
//        }




        //分布式锁实现方案
        Long userId = UserHolder.getUser().getId();
        String key = "seckillVoucherOrder" + userId.toString();
        RLock lock = redissonClient.getLock(key);
        //分布式锁的redisson方案,第一个参数表示阻塞重试时长
        boolean isLock = lock.tryLock(1,10, TimeUnit.SECONDS);
        if (!isLock){
            return Result.fail("秒杀活动请勿重复下单");
        }
        try {
            //解决事务失效问题,获取代理对象
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            //实现业务并返回订单
            return proxy.createVoucherOrder(voucherId);
        } finally {
            //手动释放锁,服务宕机靠超时剔除
            lock.unlock();
        }
    }

    @Transactional
    public Result createVoucherOrder(Long voucherId){
        //查询数据库避免重复下单
        int count = query().eq("user_id",UserHolder.getUser().getId()).eq("voucher_id",voucherId).count();
        if (count > 0){
            return Result.fail("请勿重复下单");
        }

        //扣减库存(乐观锁CAS法)
        boolean updateResult = seckillVoucherService
                .update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock",0)
//                .eq("stock",seckillVoucher.getStock())
                .update();
        if (!updateResult){
            return Result.fail("优惠券库存不足");
        }
        //创建订单
        long orderId = globalUniqueIDGenerator.getGlobalUniqueID("order");
        VoucherOrder voucherOrder = VoucherOrder
                .builder()
                .id(orderId)
                .voucherId(voucherId)
                .userId(UserHolder.getUser().getId())
                .build();
        this.save(voucherOrder);
        return Result.ok(voucherOrder);
    }


}
