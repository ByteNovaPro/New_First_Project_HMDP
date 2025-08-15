package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisIdWorker;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    IShopService shopService;
    @Autowired
    RedisIdWorker redisIdWorker;
@Test
    public void test() throws InterruptedException {
    shopService.saveShopToRedis(1L, 1L);
}

    private ExecutorService es = Executors.newFixedThreadPool(500);
    @Test
    void testIdWorker() throws InterruptedException {
        //多线程处理问题例子，可以写到简历咯
        CountDownLatch latch = new CountDownLatch(300);

        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println("id = " + id);
            }
            latch.countDown();
        };
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        latch.await();
        long end = System.currentTimeMillis();
        //30000次循环 time ≈ 1秒
        System.out.println("time = " + (end - begin));

    }

    @Test
    void testIdWorkerWithNomMltithreading(){
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 30000; i++) {
            long id = redisIdWorker.nextId("order");
            System.out.println("id = " + id);
        }
        long end = System.currentTimeMillis();
        //30000次循环 time ≈ 18秒
        System.out.println("time = " + (end - begin));
    }
}
