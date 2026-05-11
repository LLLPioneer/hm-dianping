package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {


    private final StringRedisTemplate stringRedisTemplate;


    public ShopServiceImpl(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public Result igetById(Long id) {
        //解决缓存穿透和缓存击穿
        Shop shop = cacheBreakdownAndCachePenetration(id);
        if (shop==null){
            return Result.fail(SystemConstants.SHOP_ID_IS_ERROR);
        }
        return Result.ok(shop);
    }

    //更新接口(缓存更新策略)
    @Transactional
    public Result iupdate(Shop shop){
        //先操作数据库再删除缓存线程安全问题最低
        try {
            updateById(shop);
            String key = RedisConstants.CACHE_SHOP_KEY + shop.getId();
            stringRedisTemplate.delete(key);
        } catch (Exception e) {
            return Result.fail("更新失败");
        }
        return Result.ok();
    }


    // TODO 1.锁添加唯一标识 2.double check逻辑优化
    //互斥锁解决缓存击穿和缓存击穿
    private Shop cacheBreakdownAndCachePenetration(Long id){
        if (id == null){
            return null;
        }
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        //在缓存中查店铺数据
        String shopJSON = stringRedisTemplate.opsForValue().get(key);
        //若查到的是存在的数据直接返回(缓存命中存在数据)
        if (StrUtil.isNotBlank(shopJSON)){
            return JSONUtil.toBean(shopJSON,Shop.class);
        }
        //若查到的是空字符串,表示是不存在的数据,解决缓存穿透(缓存命中空数据)
        if (shopJSON != null){
            return null;
        }


        //未命中缓存,查数据库,开始缓存重建
        String lockKey = "cacheRebuildLock:shop:"+id;
        Shop shop;

        try {
            boolean lock = tryGetLock(lockKey);
            while (!lock){
                Thread.sleep(50);
                String shopJson = stringRedisTemplate.opsForValue().get(key);
                if (StrUtil.isNotBlank(shopJson)){
                    return JSONUtil.toBean(shopJson,Shop.class);
                }
                if (shopJson!=null){
                    return null;
                }
                lock = tryGetLock(lockKey);
            }

            //拿到锁后再次检查缓存,缓存中依然没有->开始查数据库
            String shopJson = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(shopJson)){
                return JSONUtil.toBean(shopJson,Shop.class);
            }
            if (shopJson!=null){
                return null;
            }

            shop = getById(id);
            //解决缓存穿透问题
            //如果shop没有查到结果,向缓存中存入短过期时间的空字符,返回错误信息
            if (shop==null){
                stringRedisTemplate.opsForValue().set(key,"",RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            //shop查到结果,存入缓存返回结果
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL,TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            releaseLock(lockKey);
        }
        return shop;
    }
    //获取互斥锁
    private boolean tryGetLock(String key){
        Boolean result = stringRedisTemplate.opsForValue().setIfAbsent(key, "lock", 10, TimeUnit.SECONDS);
        //用工具类是因为结果可能为空,包装类自动拆箱会出现空指针异常
        return BooleanUtil.isTrue(result);
    }
    //释放互斥锁
    private void releaseLock(String key){
        stringRedisTemplate.delete(key);
    }
}
//    //解决缓存穿透
//    private Shop cachePenetration(Long id){
//
//        String key = RedisConstants.CACHE_SHOP_KEY + id;
//        //在缓存中查店铺数据
//        String shopJSON = stringRedisTemplate.opsForValue().get(key);
//        //若查到的是存在的数据直接返回
//        if (StrUtil.isNotBlank(shopJSON)){
//            return JSONUtil.toBean(shopJSON,Shop.class);
//        }
//        //若查到的是空字符串,表示是不存在的数据,解决缓存穿透
//        if (shopJSON != null){
//            return null;
//        }
//        //未命中缓存
//       return ;
//    }