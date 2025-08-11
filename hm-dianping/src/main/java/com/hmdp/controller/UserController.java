package com.hmdp.controller;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.BeanUtils;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.entity.UserInfo;
import com.hmdp.service.IUserInfoService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.baomidou.mybatisplus.core.toolkit.Wrappers.query;
import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    @Resource
    private RedisTemplate<String, String> redisTemplate;




    /**
     * 发送手机验证码
     */
    @PostMapping("/code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        // 1.检验手机号是否符合格式,如果手机号不符合格式，返回错误信息
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        // 2.如果手机号符合格式,生成短信验证码,并将验证码保存到session
        // 2.1 判断redis中是否已经存在该手机号的验证码，若存在则直接返回
        String code = redisTemplate.opsForValue().get(LOGIN_CODE_KEY+phone);
        if(code == null){
            // 2.2 若不存在则创建新的验证码并返回
            String new_code = RandomUtil.randomNumbers(6);
            redisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,new_code,5, TimeUnit.MINUTES);
            return Result.ok(new_code);
        }
        // 3.发送短信验证码
        return Result.ok(code);
    }

    /**
     * 登录功能
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session){
        //1.校验手机号是否为空
        if (loginForm.getPhone() == null) {
            return Result.fail("手机号不能为空");
        }
        //2.校验手机号是否符合格式
        if(RegexUtils.isPhoneInvalid(loginForm.getPhone())){
            return Result.fail("手机号格式错误");
        }
        //3.校验验证码是否为空
        if(loginForm.getCode() == null){
            return Result.fail("验证码不存在");
        }
        //4.校验验证码是否正确
        //4.1获取redis中的code
        String redis_code = redisTemplate.opsForValue().get(LOGIN_CODE_KEY+loginForm.getPhone());
        System.out.println("redis_code::::::::::::::::::"+redis_code);
        System.out.println("loginForm.getCode()"+loginForm.getCode());
        if(!Objects.equals(redis_code, loginForm.getCode())){
            return Result.fail("验证码错误");
        }
        //5.校验用户是否存在
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("phone", loginForm.getPhone());
        User user = userService.getOne(queryWrapper); // 调用 getOne 方法

        String token = UUID.randomUUID().toString();

        if(user == null){
            user = userService.creatUserWithPhone(loginForm.getPhone());
        }
        //7.将用户信息保存在redis中
        //todo 会重复在redis中创建user，待优化！！！！！！！！！也不对，下线之后应该要清除掉缓存中的user…… 可以往下继续开发了……嘿嘿……

        Map<String,Object> userMap = new HashMap<>();
        userMap.put("phone",loginForm.getPhone());
        userMap.put("nickName",user.getNickName());
        userMap.put("icon",user.getIcon());
        userMap.put("id",user.getId());
        String tokenKey = LOGIN_USER_KEY+token;
        redisTemplate.opsForHash().putAll(tokenKey, userMap);
        // 给个过期时间，防止一直占内存
        redisTemplate.expire(tokenKey, Duration.ofHours(24));
        return Result.ok(token);
    }

    /**
     * 登出功能
     * @return 无
     */
    @PostMapping("/logout")
    public Result logout(){
        String token = UserHolder.getUser().getToken();
        System.out.println("removeUser之前::::::::::"+UserHolder.getUser());
        UserHolder.removeUser();
        System.out.println("removeUser之后::::::::::"+UserHolder.getUser());
        redisTemplate.delete(token);
        System.out.println("成功退出成功退出成功退出成功退出成功退出成功退出成功退出成功退出");

        return Result.ok("成功退出");
    }

    @GetMapping("/me")
    public Result me(){
        UserDTO user = UserHolder.getUser();

        return Result.ok(user);
    }

    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId){
        // 查询详情
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            // 没有详情，应该是第一次查看详情
            return Result.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        // 返回
        return Result.ok(info);
    }
}
