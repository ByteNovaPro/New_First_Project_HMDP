package com.hmdp.service.impl;


import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;

import com.hmdp.utils.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;


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
    private IShopService shopService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Override
    //todo 用布隆过滤器解决穿透问题
    public Result queryShopById(Long id) {
        //1.根据id在redis中查询店铺信息
        String shopCached = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //如果存在则直接返回
        if(StrUtil.isNotBlank(shopCached)){
            Shop shop = JSONUtil.toBean(shopCached,Shop.class);
            return Result.ok(shop);
        }
        if(shopCached!=null&&shopCached.equals("")){
            System.out.println("shopCached:::"+shopCached);
            System.out.println("我走这一步了哟");
            return Result.fail("店铺不存在");
        }
            //3.如果shopCached==null,根据id查询数据库，后续数据库有数据就返回数据，没数据就把""存到redis
            Shop shop = getById(id);
            if(shop == null){
                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,"",2, TimeUnit.MINUTES);
                return Result.fail("店铺不存在");
            }
        //2.如果存在，将店铺信息保存到redis中,直接返回
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(shop),10, TimeUnit.MINUTES);
            return Result.ok(shop);

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
}
