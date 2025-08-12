package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.utils.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.TYPE_LIST_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private IShopTypeService typeService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryTypeList() {
        //1.查询类型列表
        String typeList = stringRedisTemplate.opsForValue().get(TYPE_LIST_KEY);
        if(StrUtil.isNotBlank(typeList)){
            return Result.ok(JSONUtil.toList(typeList,ShopType.class));
        }
        //在数据库中查询
        List<ShopType> typeList_Database = typeService.query().orderByAsc("sort").list();
        //2.如果不存在则返回错误信息
        if (CollectionUtils.isEmpty(typeList_Database)) {
            return Result.fail("类型不存在");
        }
        //3.将类型列表存入redis，返回列表
        stringRedisTemplate.opsForValue().set(TYPE_LIST_KEY, JSONUtil.toJsonStr(typeList_Database),24, TimeUnit.HOURS);
        return Result.ok(typeList_Database);
    }
}
