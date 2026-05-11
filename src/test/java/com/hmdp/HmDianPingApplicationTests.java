package com.hmdp;

import com.hmdp.utils.GlobalUniqueIDGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


@SpringBootTest
class HmDianPingApplicationTests {



//    @Autowired
//    private StringRedisTemplate stringRedisTemplate;
//    @Autowired
//    private RedisTemplate<String,String> redisTemplate;
    @Autowired
    private GlobalUniqueIDGenerator globalUniqueIDGenerator;

    private ExecutorService es = Executors.newFixedThreadPool(500);



@Test
public void test1(){
//    stringRedisTemplate.opsForHash().put("user:400", "name", "虎哥");
//    stringRedisTemplate.opsForHash().put("user:400", "age", "21");


//    redisTemplate.opsForHash().put("user:700", "name", "虎哥");
//
//redisTemplate.opsForHash().put("user:700", "age", "21");


//    stringRedisTemplate.opsForValue().set();



}


@Test
void globalUniqueIDGeneratorTest() throws InterruptedException {
    CountDownLatch countDownLatch = new CountDownLatch(300);
    long start = System.currentTimeMillis();
    for (int i = 0; i < 300; i++) {
        es.submit(()->{
            for (int j = 0; j < 100; j++) {
                System.out.println(globalUniqueIDGenerator.getGlobalUniqueID("order"));
            }
            countDownLatch.countDown();
        });
    }
    countDownLatch.await();
    long end = System.currentTimeMillis();
    System.out.println("time:"+(end-start));
}

    public static void main(String[] args) {
//        LocalDateTime localDateTime = LocalDateTime.now();
//        System.out.println(localDateTime.toEpochSecond(ZoneOffset.UTC));



//        long epochSecond = Instant.now().getEpochSecond();
//        long a = (epochSecond << 32) | 32;
//        byte b = (byte) a;
//        System.out.println((byte)256);
//        System.out.println(UUID.randomUUID());
//        long startEpochSecond = Instant.parse("").getEpochSecond();
//        System.out.println(startEpochSecond);
//        LocalDate date = LocalDate.now();
//        String dateStr  = date.toString().replaceAll("-",":");
//        System.out.println(dateStr);
        long startEpochSecond = Instant.parse("2020-01-01T00:00:00.00Z").getEpochSecond();
        System.out.println(startEpochSecond);
        long nowEpochSecond = Instant.now().getEpochSecond();
        System.out.println(nowEpochSecond);
        long epoch = nowEpochSecond - startEpochSecond;
        System.out.println(epoch);
    }


}
