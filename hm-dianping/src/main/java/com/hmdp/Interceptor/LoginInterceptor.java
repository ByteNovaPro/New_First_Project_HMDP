package com.hmdp.Interceptor;

import com.hmdp.utils.dto.UserDTO;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class LoginInterceptor implements HandlerInterceptor {

    @Resource
    private RedisTemplate redisTemplate;
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        //1.判断是否需要拦截（ThreadLocal中是否有用户）
        UserDTO user = UserHolder.getUser();
        if (user == null){
            response.setStatus(401);
            return false;
        }

        return true;
    }
}
