package com.hmdp.utils;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{

    private String name;
    private StringRedisTemplate stringRedisTemplate;
    private static final String key_prefix = "lock:";
    private static final String uuid = UUID.randomUUID()+"-";
    public SimpleRedisLock(String name,StringRedisTemplate stringRedisTemplate){
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }
    @Override
    public boolean tryLock(long timeoutSec) {
        //获取当前线程id
        long currentThreadId = Thread.currentThread().getId();
        //尝试获取锁
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(key_prefix+name,uuid+currentThreadId,timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

//    @Override
//    public void unlock() {
//        //获取当前线程标识
//        String threadId = uuid+Thread.currentThread().getId();
//        //获取这个业务或者用户目前实际在redis中存储的线程标识
//        String success = stringRedisTemplate.opsForValue().get(key_prefix+name);
////？？？？？？？？！！！！！ 需要保证原子性操作，判断锁 和 删除锁
//        if (success == threadId){
//            //释放锁
//            stringRedisTemplate.delete(key_prefix+name);
//        }
//    }
    private static final String ID_PREFIX = UUID.randomUUID()+"-";
    private static final String KEY_PREFIX = "lock:";
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }
    //使用lua脚本解锁，确保操作原子性
    @Override
    public void unlock() {
        // 调用lua脚本
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),
                ID_PREFIX + Thread.currentThread().getId());
    }
}
