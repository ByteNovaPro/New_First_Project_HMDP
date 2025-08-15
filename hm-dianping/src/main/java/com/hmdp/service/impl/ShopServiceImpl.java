package com.hmdp.service.impl;


import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static org.apache.tomcat.jni.Time.sleep;


/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    CacheClient cacheClient;
    @Autowired
    private IShopService shopService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Override

    public Result queryShopById(Long id) {
        //解决缓存穿透
//        Shop shop = cacheClient
//                .queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        // 互斥锁解决缓存击穿
         Shop shop = cacheClient
                 .queryWithMutex(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 逻辑过期解决缓存击穿
        // Shop shop = cacheClient
        //         .queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, 20L, TimeUnit.SECONDS);

        if (shop == null) {
            return Result.fail("店铺不存在！");
        }
        return Result.ok(shop);
    }

    //创建线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    public Shop queryShopById_Thrashing(Long id){
        //1.查询店铺数据
        String shop_json = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY+id);
        //2.判断缓存是否存在
        if(StrUtil.isBlank(shop_json)){
            return null;
        }
        RedisData shop_redis_data = JSONUtil.toBean(shop_json,RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) shop_redis_data.getData(), Shop.class);
        //3.判断是否过期
        if(!LocalDateTime.now().isAfter(shop_redis_data.getExpireTime())){
            //4.没有过期
            return shop;
        }
        //5.过期
        //获取互斥锁
        boolean flag = tryLock(id);
        //是，开启独立线程，根据id查询数据库，将数据库的数据写入Redis并设置过期时间
        if (flag){
            //使用线程池 (CACHE_REBUILD_EXECUTOR) 避免阻塞主线程
            //适合耗时操作（如DB查询、网络请求）
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    //重建缓存
                    this.saveShopToRedis(id,20l);
                }catch (Exception e){
                    e.printStackTrace();
                }
                finally {
                    //释放锁
                    unLock(id);
                }
            });
        }
        //否，直接返回商铺信息
        return shop;
    }
    public Shop queryShopById_Thrashing_Cache_Miss(Long id){
        //缓存击穿（互斥锁）和穿透（设置null）
        try {
            //1.根据id在redis中查询店铺信息
            String shopCached = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
            //如果存在则直接返回
            if (StrUtil.isNotBlank(shopCached)) {
                Shop shop = JSONUtil.toBean(shopCached, Shop.class);
                return shop;
            }
            if (shopCached != null && shopCached.equals("")) {
                return null;
            }
            //重建缓存数据
            //获取互斥锁
            Boolean flag = tryLock(id);
            //如果获取失败,则休眠重试
            if (!flag) {
                Thread.sleep(200);
                return queryShopById_Thrashing_Cache_Miss(id);
            }
            //3.如果shopCached==null,根据id查询数据库，后续数据库有数据就返回数据，没数据就把""存到redis
            Shop shop = getById(id);
            if (shop == null) {
                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", 2, TimeUnit.MINUTES);
                return null;
            }
            //2.如果存在，将店铺信息保存到redis中,直接返回
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop), 10, TimeUnit.MINUTES);
            return shop;
        } catch(InterruptedException e){
            throw new RuntimeException(e);
        }finally {
            unLock(id);
        }
    }

    public Shop queryShopById_Cache_Miss(Long id){
        //todo 用布隆过滤器解决穿透问题
        //1.根据id在redis中查询店铺信息
        String shopCached = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //如果存在则直接返回
        if(StrUtil.isNotBlank(shopCached)){
            Shop shop = JSONUtil.toBean(shopCached,Shop.class);
            return shop;
        }
        if(shopCached!=null&&shopCached.equals("")){
            return null;
        }
        //3.如果shopCached==null,根据id查询数据库，后续数据库有数据就返回数据，没数据就把""存到redis
        Shop shop = getById(id);
        if(shop == null){
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,"",2, TimeUnit.MINUTES);
            return null;
        }
        //2.如果存在，将店铺信息保存到redis中,直接返回
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(shop),10, TimeUnit.MINUTES);
        return shop;

    }


    @Override
    @Transactional
    public Result updateShop(Shop shop) {
        // 1.判断是否存在
        if(shop.getId() == null){
            return Result.fail("店铺id不能为空");
        }
        // 1.先更新数据库
        shopService.updateById(shop);
        // 2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }

    public Boolean tryLock(Long id){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(LOCK_SHOP_KEY + id, "1", 10, TimeUnit.SECONDS);
        return flag;
    }
    public void unLock(Long id){
        stringRedisTemplate.delete(LOCK_SHOP_KEY + id);
    }
    public void saveShopToRedis(Long id,Long expiredSeconds) throws InterruptedException {
        Shop shop = shopService.getById(id);
        Thread.sleep(200);
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expiredSeconds));
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(redisData));
        System.out.println(redisData);
    }
}
