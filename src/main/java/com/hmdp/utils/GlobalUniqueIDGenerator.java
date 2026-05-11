package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;

@Component
public class GlobalUniqueIDGenerator {

    private final StringRedisTemplate stringRedisTemplate;
    //时间戳从2020年1月1日00:00:00开始的
    private final static long startEpochSecond = Instant.parse("2020-01-01T00:00:00.00Z").getEpochSecond();
    private final static short COUNT_BIT_SIZE = 32;


    public GlobalUniqueIDGenerator(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public long getGlobalUniqueID(String keyPrefix){
        //获取时间戳
        long nowEpochSecond = Instant.now().getEpochSecond();
        long epoch = nowEpochSecond - startEpochSecond;

        //获取序列号
        LocalDate date = LocalDate.now();
        String dateStr  = date.toString().replaceAll("-",":");//替换成:方便后续按年月统计
        String key = keyPrefix + dateStr + ":increment";
        //不会导致空指针,如果key不存在会自动创建key
        long count = stringRedisTemplate.opsForValue().increment(key);

        //拼接返回
        return (epoch << COUNT_BIT_SIZE) | count;
    }



}
