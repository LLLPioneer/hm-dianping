package com.hmdp.utils;


import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Component
public class SimpleRedisLock implements Lock{

    private final StringRedisTemplate stringRedisTemplate;
    private final static String KEY_PREFIX = "lock:redisLock";
    private final static String UNIQUE_SIGN = UUID.randomUUID(true).toString();
    private final static DefaultRedisScript<Long> RELEASE_REDIS_LOCK_LUA_SCRIPT;
    static {
        RELEASE_REDIS_LOCK_LUA_SCRIPT = new DefaultRedisScript<>();
        RELEASE_REDIS_LOCK_LUA_SCRIPT.setLocation(new ClassPathResource("ReleaseRedisLockScript.lua"));
    }

    public SimpleRedisLock(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate = stringRedisTemplate;
    }


    /**
     *
     * @param keyName 设置锁key的名字
     * @param ttl 设置锁自动过期的时间,根据实际业务而定
     * @return 返回是否获取锁成功
     */
    @Override
    public boolean tryLock(String keyName, Long ttl) {
        //获取当前线程id作为value
        String key = KEY_PREFIX + keyName;
        String threadSign = UNIQUE_SIGN+"-"+Thread.currentThread().getId();
        Boolean result = stringRedisTemplate.opsForValue().setIfAbsent(key, threadSign, ttl, TimeUnit.SECONDS);
        //防止自动拆箱时的空指针异常
        return Boolean.TRUE.equals(result);
    }

    /**
     * lua脚本保证原子性
     * <p>
     * -- 如果redis中的线程标识(通过传入的key获取)与当前的线程标识(通过参数传入)相等删除锁
     * if(redis.call('get',KEYS[1]) == ARGV[1]) then
     *     return redis.call('del',KEYS[1])
     * end
     * return 0
     * <p/>
     * @param keyName 传入锁key的名字释放锁,要与设置时一致
     */
    @Override
    public void releaseLock(String keyName) {
        //删除前先检查是不是自己的锁,防止误删,检查与删除间存在原子性问题
        String key = KEY_PREFIX + keyName;
        String currentThreadSign = UNIQUE_SIGN+"-"+Thread.currentThread().getId();
        //使用lua脚本确保原子性
        stringRedisTemplate.execute(RELEASE_REDIS_LOCK_LUA_SCRIPT, Collections.singletonList(key),currentThreadSign);

//        String nowRedisThreadSign = stringRedisTemplate.opsForValue().get(key);
//        if (nowRedisThreadSign ==null || !nowRedisThreadSign.equals(currentThreadSign)){
//            return;
//        }
//        stringRedisTemplate.delete(KEY_PREFIX + keyName);
    }
}
