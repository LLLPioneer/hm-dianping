package com.hmdp.utils;


import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.*;
import java.util.function.Function;

@Component
@Slf4j
public class RedisUtils {

    private final StringRedisTemplate stringRedisTemplate;
    private final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public RedisUtils(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * @param key 缓存key
     * @param value 缓存对象
     * @param ttl 逻辑过期时长
     * @param unit TimeUnit单位
     */
    public void setObjectCache(String key, Object value, Long ttl, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),ttl,unit);
    }

    /**
     * @param key 缓存key
     * @param value 缓存对象
     * @param ttl 逻辑过期时长
     * @param unit ChronoUnit单位(支持日历单位TimeUnit不支持日历单位)
     */
    public void setObjectCacheWithLogicalExpire(String key,Object value,Long ttl,ChronoUnit unit){

        LocalDateTime expireTime = LocalDateTime.now().plus(ttl,unit);

        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(RedisData.builder().expireTime(expireTime).data(value).build()));

    }

    /**
     * CacheBreakdownAndCachePenetration,解决缓存击穿(互斥锁方案)和缓存穿透
     * @param keyPrefix 缓存key的前缀
     * @param id 查询id
     * @param type 返回值类型
     * @param ttl key的逻辑过期时长
     * @param unit 时长单位
     * @param dbFunction 缓存重建所用的数据库查询函数
     * @param isLogicalExpire 是否为逻辑过期key
     * @return 返回查询结果,null代表未查询到缓存
     * @param <R> 需要返回的示例类型
     * @param <ID> id的数据类型
     * @throws Exception 检查异常,线程等待异常
     */
    public <R,ID> R getObject1(String keyPrefix, ID id,Long ttl,TimeUnit unit,boolean isLogicalExpire,Class<R> type, Function<ID,R> dbFunction) throws Exception {
        //getObject1方法不用于处理逻辑过期的key
        if (isLogicalExpire){
            throw new Exception("getObject1方法不用于处理逻辑过期的key");
        }

        if (id == null||StrUtil.isBlank(keyPrefix)){
            return null;
        }
        String key = keyPrefix + id;
        //在缓存中查数据
        String jsonStr = stringRedisTemplate.opsForValue().get(key);
        //若查到的是存在的数据直接返回(缓存命中存在数据)
        if (StrUtil.isNotBlank(jsonStr)){
            return JSONUtil.toBean(jsonStr,type);
        }
        //若查到的是空字符串,表示是不存在的数据,解决缓存穿透(缓存命中空数据)
        if (jsonStr != null){
            return null;
        }

        //未命中缓存,查数据库,开始缓存重建
        String lockKey = "cacheRebuildLock"+":"+keyPrefix+id;
        R result;
        try {
            boolean lock = tryGetLock(lockKey);
            while (!lock){
                Thread.sleep(50);
                 jsonStr = stringRedisTemplate.opsForValue().get(key);
                if (StrUtil.isNotBlank(jsonStr)){
                    return JSONUtil.toBean(jsonStr,type);
                }
                if (jsonStr!=null){
                    return null;
                }
                lock = tryGetLock(lockKey);
            }

            //拿到锁后再次检查缓存,缓存中依然没有->开始查数据库
            //缓存中已经有数据了->返回数据释放锁
            jsonStr = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(jsonStr)){
                releaseLock(lockKey);
                return JSONUtil.toBean(jsonStr,type);
            }
            if (jsonStr!=null){
                releaseLock(lockKey);
                return null;
            }

            result = dbFunction.apply(id);
            //解决缓存穿透问题
            //如果result没有查到结果,向缓存中存入短过期时间的空字符,返回错误信息
            if (result==null){
                stringRedisTemplate.opsForValue().set(key,"",RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            //result查到结果,存入缓存返回结果
            this.setObjectCache(key,result,ttl,unit);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            releaseLock(lockKey);
        }
        return result;
    }

    /**
     * @param keyPrefix 缓存key的前缀
     * @param id 查询id
     * @param ttl key的逻辑过期时长
     * @param unit 时长单位
     * @param isLogicalExpire 是否为逻辑过期key
     * @param type 返回值类型
     * @param dbFunction 缓存重建所用的数据库查询函数
     * @return 返回查询结果(可能为旧数据),null代表未查询到数据
     * @param <R> 需要返回的示例类型
     * @param <ID> id的数据类型
     * @throws Exception 检查异常
     */
    public <R,ID> R getObject2(String keyPrefix, ID id,Long ttl,ChronoUnit unit,boolean isLogicalExpire,Class<R> type, Function<ID,R> dbFunction) throws Exception {
        //getObject2方法用于处理逻辑过期的key
        if (!isLogicalExpire){
            throw new Exception("getObject2方法用于处理逻辑过期的key");
        }
        if (id == null||StrUtil.isBlank(keyPrefix)){
            return null;
        }
        //在缓存中查数据(逻辑过期方案缓存需要提前预热,查到空字符或缓存未命中都返回null)
        String key = keyPrefix + id;
        String jsonStr = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(jsonStr)){
            return null;
        }

        RedisData redisData = JSONUtil.toBean(jsonStr,RedisData.class);
        R result =  JSONUtil.toBean((JSONObject) redisData.getData(),type);
        LocalDateTime expireTime = redisData.getExpireTime();
        //未过期直接返回
        if (expireTime.isAfter(LocalDateTime.now())){
            return result;
        }

        //已过期开始重建缓存
        String lockKey = "cacheRebuildLock"+":"+keyPrefix+id;
        boolean isLock = tryGetLock(lockKey);
        //获取锁成功开启独立线程重建缓存
        if (isLock){
            //获取锁后再次检查是否过期?
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                     R obj = dbFunction.apply(id);
                    this.setObjectCacheWithLogicalExpire(key,obj,ttl,unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    this.releaseLock(lockKey);
                }
            });
        }
        //无论是否获取锁都返回旧数据,让新线程异步重建缓存
        return result;
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






























