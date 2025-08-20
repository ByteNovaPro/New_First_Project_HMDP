package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import com.hmdp.utils.dto.Result;
import com.hmdp.utils.dto.UserDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Autowired
    IUserService userService;

    @Autowired
    IBlogService blogService;

    @Autowired
    StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryBlogById(Long id) {
        // 1.查询blog
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("笔记不存在！");
        }
        // 2.查询blog有关的用户
        queryBlogUser(blog);
        isBlogLike(blog);
        return Result.ok(blog);
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            this.queryBlogUser(blog);
            this.isBlogLike(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result blogLike(Long id) {
        //1.获取用户id
        UserDTO userDTO = UserHolder.getUser();
        Long userId = userDTO.getId();
        //1.判断用户是否点赞过
        String key = BLOG_LIKED_KEY + id;
//        Boolean isMember = stringRedisTemplate.opsForSet().isMember(key,userId.toString());
        Double isMember = stringRedisTemplate.opsForZSet().score(key,userId.toString());
        //2.如果用户没有点赞
        if (isMember == null){
            //2.1 数据库liked字段+1
            boolean isSuccess = blogService.update().setSql("liked = liked + 1").eq("id",id).update();
            if (isSuccess){
                //2.2 set集合存入用户id
                stringRedisTemplate.opsForZSet().add(key,userId.toString(),System.currentTimeMillis());
            }else {
                return Result.fail("点赞失败");
            }
        }else{
            //3如果用户点赞过
            //3.1数据库liked字段-1
            boolean isSuccess = blogService.update().setSql("liked = liked - 1").eq("id",id).update();
            if (isSuccess){
                //3.2set集合移除用户id
                stringRedisTemplate.opsForZSet().remove(key,userId.toString());
            }else {
                return Result.fail("取消点赞失败");
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        String key = BLOG_LIKED_KEY + id;
        // 1.查询top5的点赞用户 zrange key 0 4
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (top5 == null || top5.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        // 2.解析出其中的用户id
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        String idStr = StrUtil.join(",", ids);
        // 3.根据用户id查询用户 WHERE id IN ( 5 , 1 ) ORDER BY FIELD(id, 5, 1)
        List<UserDTO> userDTOS = userService.query()
                .in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        // 4.返回
        return Result.ok(userDTOS);
    }


    private void queryBlogUser(Blog blog) {
        //1.根据userId在数据库查到user信息
        User user = userService.getById(blog.getUserId());
        //2.将user信息传入blog中
        blog.setIcon(user.getIcon());
        blog.setName(user.getNickName());
    }
    private void isBlogLike(Blog blog){
        String key = BLOG_LIKED_KEY + blog.getId();
        Double isSuccess = stringRedisTemplate.opsForZSet().score(key,UserHolder.getUser().getId().toString());
        if (isSuccess!=null){
            blog.setIsLike(true);
        }
        else {
            blog.setIsLike(false);
        }
    }
}
