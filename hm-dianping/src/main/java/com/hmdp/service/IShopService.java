package com.hmdp.service;

import com.hmdp.utils.dto.Result;
import com.hmdp.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

import java.time.LocalDateTime;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {

    Result queryShopById(Long id) ;
    Result updateShop(Shop shop);
    void saveShopToRedis(Long id, Long expiredSeconds) throws InterruptedException;
}
