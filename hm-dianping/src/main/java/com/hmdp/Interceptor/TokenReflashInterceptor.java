package com.hmdp.Interceptor;

import com.hmdp.utils.dto.UserDTO;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Map;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;


@Slf4j
@Component
public class TokenReflashInterceptor implements HandlerInterceptor {
    @Autowired
    private RedisTemplate redisTemplate;
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception{

        //1.拿到token
        String token = request.getHeader("Authorization");
        //2.判断token是否为空，如果为空则直接放行，没有登陆态
        if(token == null){
            log.info("token为空，前端未携带token，直接放行，没有登陆态");
            return true;
        }
        //3.查询token是否在redis中能够查询得到 ，需要用户重新登陆
        if((redisTemplate.opsForHash().entries(LOGIN_USER_KEY+token)).isEmpty()){
            log.info("token已经过期，请用户重新登录");
            return true;
        }
        //4.如果能查询的到，就刷新token，将token保存到threadLocal中
        //刷新token
        redisTemplate.expire(LOGIN_USER_KEY+token, Duration.ofHours(24));
        //将token保存到threadLocal中
        UserDTO userDTO = new UserDTO();
        Map userDTOMap = redisTemplate.opsForHash().entries(LOGIN_USER_KEY+token);
        userDTO.setIcon((String)userDTOMap.get("icon"));
        userDTO.setNickName((String)userDTOMap.get("nickName"));
        userDTO.setToken(LOGIN_USER_KEY+token);
        userDTO.setPhone((String)userDTOMap.get("phone"));
        userDTO.setId(Long.valueOf(userDTOMap.get("id").toString()));
        if(UserHolder.getUser() == null){
            UserHolder.saveUser(userDTO);
        }
        //5.放行
        return true;
    }
}
